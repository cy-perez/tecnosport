# API

REST sobre HTTP, JSON, versionada en la ruta: `/api/v1`. El contrato lo genera
springdoc desde el código y se publica en `/api/openapi.json`. Ese archivo es la
fuente del cliente TypeScript de `packages/contratos`. No se escriben interfaces
de respuesta a mano.

## Convenciones

- Recursos en plural y en español: `/api/v1/productos`, `/api/v1/pedidos`.
- Campos JSON en `camelCase`.
- Fechas ISO-8601 en UTC: `2026-08-30T14:05:00Z`.
- Dinero como entero de pesos más el código de moneda:
  `{ "valor": 189900, "moneda": "COP" }`. Nunca un texto con separadores.
- Paginación por cursor en el catálogo, por página en el panel administrativo. La
  respuesta siempre trae `items` y metadatos, nunca un arreglo desnudo.

## Errores

Un solo formato, `application/problem+json` según RFC 9457:

```json
{
  "type": "https://tecnosport.co/errores/existencia-insuficiente",
  "title": "Existencia insuficiente",
  "status": 409,
  "detail": "Quedan 2 unidades de TS-CAM-AZ-M",
  "instance": "/api/v1/pedidos",
  "codigo": "EXISTENCIA_INSUFICIENTE",
  "campos": []
}
```

`codigo` es estable y es lo que el frontend usa para elegir el mensaje traducido.
El `detail` nunca se muestra tal cual al usuario: es para el registro. Los errores
de validación devuelven 422 con `campos` poblado. Nunca se filtra una traza, un
nombre de tabla ni un mensaje de PostgreSQL.

## Salud

`GET /api/v1/salud` responde `200` con el texto plano `OK`, sin envolver en
JSON: es para probes de infraestructura (Cloud Run, balanceador), no para el
frontend, así que no sigue la convención JSON del resto del contrato.

## Endpoints públicos

```
GET  /api/v1/productos                      filtros, orden, cursor
GET  /api/v1/productos/{slug}               incluye imágenes y set de rotación
GET  /api/v1/categorias
GET  /api/v1/marcas
POST /api/v1/carritos
GET  /api/v1/carritos/{id}
POST /api/v1/carritos/{id}/lineas
PATCH /api/v1/carritos/{id}/lineas/{lineaId}
DELETE /api/v1/carritos/{id}/lineas/{lineaId}
POST /api/v1/envios/cotizacion              destino y contenido, devuelve opciones
GET  /api/v1/envios/cobertura               ciudades con contraentrega habilitada
POST /api/v1/pedidos                        revalida precios y existencias, reserva
POST /api/v1/pagos/intentos                 crea el intento en la pasarela
POST /api/v1/pagos/webhook                  eventos de Wompi, firma verificada
GET  /api/v1/pedidos/{id}/seguimiento       con token del correo, sin sesión
```

### Filtros, orden y paginación de `GET /api/v1/productos`

| Parámetro | Qué hace |
|---|---|
| `categoria` | slug de la categoría |
| `marca` | id de la marca |
| `linea` | `ROPA_Y_CALZADO`, `BOLSOS` o `CELULARES` |
| `precioMin`, `precioMax` | rango sobre el precio "desde" del producto (el menor precio entre sus variantes activas, el precio vive en la variante) |
| `texto` | búsqueda libre por nombre, por similitud (`pg_trgm`), no exige substring exacto |
| `orden` | `RELEVANCIA` (predeterminado; sin `texto` cae a `MAS_RECIENTES`), `PRECIO_ASC`, `PRECIO_DESC`, `MAS_RECIENTES` |
| `cursor` | opaco — viene de `cursorSiguiente` de la página anterior, nunca se construye a mano |
| `tamano` | entero entre 1 y 60, 24 por defecto |

La respuesta es `{ "items": [...], "cursorSiguiente": "..." }`.
`cursorSiguiente` es `null` cuando no hay más páginas.

`GET /api/v1/categorias` y `GET /api/v1/marcas` no tienen parámetros —listas
completas, sin paginar, porque son pocos registros— y devuelven la misma
envoltura `{ "items": [...], "cursorSiguiente": null }` que el catálogo
paginado, nunca un arreglo desnudo. Si el catálogo de categorías o marcas
crece mucho, esto necesitará paginar igual que `/productos`.

Las variantes con `estado == INACTIVA` nunca aparecen en `variantes` de la
ficha pública: mismo principio que `Producto.estado == PUBLICADO`, el
servidor no expone lo que dio de baja.

El set de rotación viaja dentro de la ficha del producto, ya ordenado y con las
URL absolutas:

```json
"rotacion": {
  "fotogramas": 8,
  "imagenes": [
    { "orden": 0, "url": "...", "urlWebp": "...", "ancho": 1000, "alto": 1000 }
  ]
}
```

El frontend no calcula ni adivina el orden. Si el set está incompleto, el campo
`rotacion` viene nulo y no se envían fotogramas sueltos.

## Endpoints con sesión

```
POST /api/v1/auth/registro | /sesion | /refresco | /cierre
POST /api/v1/auth/verificacion | /recuperacion
GET  /api/v1/cuenta/pedidos
GET/POST/PATCH /api/v1/cuenta/direcciones
```

## Endpoints de administración

Rol `ADMIN`.

```
GET/POST/PATCH /api/v1/admin/productos
GET/POST /api/v1/admin/variantes/{id}/inventario
GET/PATCH /api/v1/admin/pedidos
POST /api/v1/admin/pedidos/{id}/despacho            transportadora y guía
POST /api/v1/admin/pedidos/{id}/recaudo             concilia contraentrega

POST /api/v1/admin/sets-rotacion                    abre un set en BORRADOR
POST /api/v1/admin/sets-rotacion/{id}/subidas       N URL firmadas, una por fotograma
POST /api/v1/admin/sets-rotacion/{id}/completar     valida y pasa a COMPLETO
DELETE /api/v1/admin/sets-rotacion/{id}
```

Las imágenes se suben **directo a Cloud Storage con URL firmada**. No pasan por el
backend. El servidor emite las URL, y al completar el set verifica que los N
objetos existan, que tengan el tamaño y la proporción esperados, y que ninguno
esté vacío. Un set que no pasa esa validación se queda en `BORRADOR`.

## Idempotencia

Todo `POST` que mueva dinero o inventario acepta la cabecera `Idempotency-Key`.
La llave se persiste con su respuesta durante 24 horas: un reintento con la misma
llave devuelve la misma respuesta, no crea un segundo pedido. El frontend genera
un UUID por intento del usuario, no por reintento HTTP.

El webhook de Wompi es idempotente por identificador de evento: llega repetido y
tiene que ser inofensivo.

## Reglas que el backend nunca delega al cliente

1. Recalcular precio, IVA y total.
2. Verificar existencias.
3. Calcular el costo de envío que se cobra.
4. Decidir si un método de pago está disponible para ese destino y ese monto.
5. Decidir si un pago está aprobado. La verdad es la consulta a la pasarela, no
   el parámetro que trae el navegador al volver.
