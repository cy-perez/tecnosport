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
GET  /api/v1/envios/cobertura               ciudades con contraentrega habilitada
POST /api/v1/pedidos/metodos-de-pago-disponibles   qué métodos aplican a este carrito y destino
POST /api/v1/pedidos                        revalida precios y existencias, reserva
POST /api/v1/pagos/intentos                 crea el intento en la pasarela
PATCH /api/v1/pagos/intentos/{referencia}   registra el id de transacción de Wompi al volver del checkout
POST /api/v1/pagos/webhook                  eventos de Wompi, firma verificada
POST /api/v1/pedidos/{id}/reintentar-pago   PAGO_FALLIDO -> PAGO_PENDIENTE
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

`GET /api/v1/categorias`, `GET /api/v1/marcas` y `GET /api/v1/atributos` no
tienen parámetros —listas completas, sin paginar, porque son pocos
registros— y devuelven la misma envoltura `{ "items": [...], "cursorSiguiente":
null }` que el catálogo paginado, nunca un arreglo desnudo. Si alguno de
estos catálogos crece mucho, esto necesitará paginar igual que `/productos`.
`/atributos` es un catálogo global, sin asociación a categoría en el
esquema (docs/02-modelo-datos.md) — el panel admin lo usa para armar el
selector de atributos al agregar una variante.

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
POST /api/v1/auth/verificacion
POST /api/v1/auth/recuperacion | /recuperacion/confirmar
GET  /api/v1/cuenta/pedidos
GET/POST/PATCH /api/v1/cuenta/direcciones
```

`/auth/recuperacion` (pedir el enlace, solo el correo) y
`/auth/recuperacion/confirmar` (token + clave nueva) son dos pasos, no uno —
`/auth/recuperacion` responde 204 siempre, exista o no una cuenta con ese
correo (docs/08-seguridad-legal.md, OWASP: no se revela cuál de los dos fue).

## Endpoints de administración

Rol `ADMIN`.

```
GET /api/v1/admin/productos                                  paginado por página, todos los estados
POST /api/v1/admin/productos                                 crea en BORRADOR, sin variantes ni imágenes
GET/PATCH /api/v1/admin/productos/{id}                       detalle y edición de nombre/descripción/marca/categoría
POST /api/v1/admin/variantes                                 crea una variante (con atributos) e inventario inicial
GET/POST /api/v1/admin/variantes/{id}/inventario              pendiente: reabastecimiento/ajuste sobre una variante ya creada
POST /api/v1/admin/productos/{id}/imagen-principal/url-subida  pide una URL firmada V4 de subida a Cloud Storage
POST /api/v1/admin/productos/{id}/imagen-principal            confirma la subida y reemplaza la imagen principal
POST/DELETE /api/v1/admin/cobertura-contraentrega[/{codigoDaneCiudad}]  carga manual, sin UI
GET /api/v1/admin/pedidos                                   paginado; ?estado= filtra y ordena por más antiguo primero
POST /api/v1/admin/pedidos/{id}/verificar-contraentrega     contacto por WhatsApp o llamada
POST /api/v1/admin/pedidos/{id}/despacho                    transportadora y guía
POST /api/v1/admin/pedidos/{id}/entrega                     marca entregado
POST /api/v1/admin/pedidos/{id}/rechazo-entrega             libera inventario, registra motivo
POST /api/v1/admin/pedidos/{id}/recaudo                     concilia contraentrega
POST /api/v1/admin/pedidos/{id}/conciliar-transferencia     concilia transferencia manual

POST /api/v1/admin/sets-rotacion                    abre un set vacío en BORRADOR
POST /api/v1/admin/sets-rotacion/{id}/subidas       N URL firmadas, una por fotograma
POST /api/v1/admin/sets-rotacion/{id}/completar     verifica los objetos y pasa a COMPLETO
POST /api/v1/admin/sets-rotacion/{id}/publicar      de COMPLETO a PUBLICADO: la ficha muestra el visor
DELETE /api/v1/admin/sets-rotacion/{id}
```

El set se abre **prometiendo cuántos fotogramas va a tener** (entre 4 y 16), y esa
promesa manda en todo lo demás: `/subidas` emite exactamente esas N URL —el
cliente no elige cuántas ni dónde escribe, la key la arma el servidor— y
`/completar` exige que hayan llegado todas. Sin la promesa, un set de 8 que
termina con 4 fotogramas contiguos pasaría por un set de 4 perfectamente válido.

Las imágenes se suben **directo a Cloud Storage con URL firmada**. No pasan por el
backend. Al completar, el servidor verifica contra el almacén real que cada objeto
existe, que no está vacío y que pertenece al set, que llegaron todos los
prometidos, y que las dimensiones **declaradas** son las de un fotograma de
rotación (cuadrado, 1000 px). Un set que no pasa esa verificación se queda en
`BORRADOR` entero, no a medias: medio set publicado es un visor roto.

De cada fotograma, el cuerpo de `/completar` lleva `orden`, `objectKey`, `ancho`,
`alto` y **`hash`**: el SHA-256 del archivo en hexadecimal, que el navegador
calcula sobre los bytes que acaba de subir. El mismo campo va en el cuerpo de
`POST /api/v1/admin/productos/{id}/imagen-principal`. El servidor exige que sea
un hash bien formado, pero no puede confirmar que corresponda al archivo sin
descargarlo: `ADR-0019`.

Lo que el servidor **no** verifica, y conviene tenerlo escrito: que los bytes
sean de verdad una imagen, que sus dimensiones reales sean las declaradas, y que
el hash sea el de ese archivo.
Comprobarlo exigiría descargar y decodificar el archivo en el backend, que es lo
que la subida directa evita — mismo riesgo aceptado que en la imagen principal
(`ADR-0016`), y por el mismo motivo: el panel lo usa solo el administrador.
Detalle en `ADR-0018`.

**Publicar es un paso aparte de completar** a propósito: entre los dos está la
revisión del set entero en el asistente (`docs/10-captura-360.md`, paso 6), que es
donde se caza el fotograma torcido. Un producto tiene a lo sumo un set publicado;
publicar sobre uno que ya lo está responde 409 y hay que borrar el anterior
primero.

La imagen principal (Fase 4) verifica menos que esto: solo que el objeto exista y
su tamaño en bytes, sin proporción esperada.

## Idempotencia

Todo `POST` que mueva dinero o inventario acepta la cabecera `Idempotency-Key`.
La llave se persiste con su respuesta durante 24 horas: un reintento con la misma
llave devuelve la misma respuesta, no crea un segundo pedido. El frontend genera
un UUID por intento del usuario, no por reintento HTTP.

El webhook de Wompi es idempotente por identificador de evento: llega repetido y
tiene que ser inofensivo. Wompi no manda un identificador de evento propio, así
que se usa el `checksum` de la firma — determinista sobre lo firmado, igual en
cada reintento del mismo evento.

`PATCH /api/v1/pagos/intentos/{referencia}` existe porque la API de Wompi
consulta transacciones por su propio id, no por la referencia que genera este
backend: sin ese id, la conciliación programada (`docs/11-pagos-y-envios.md`)
no tiene cómo revisar un pago que nunca recibió webhook.

**Excepción a la cabecera `Idempotency-Key`:** las acciones que transicionan
un pedido *ya existente* a partir de su estado actual —`conciliar-transferencia`,
`verificar-contraentrega`, `despacho`, `entrega`, `rechazo-entrega`,
`recaudo` (las seis, de panel, rol `ADMIN`) y `reintentar-pago` (pública,
sin sesión, mismo modelo de confianza que crear el pedido)— no la usan,
aunque muevan dinero o inventario. Su idempotencia sale gratis de la propia
máquina de estados de `Pedido`: un segundo `POST` sobre un pedido que ya
transicionó cae en un estado que `EstadoPedido` no admite como destino y la
petición se rechaza con 422, sin duplicar nada ni necesitar una llave
aparte. La diferencia con `POST /api/v1/pedidos` y `POST
/api/v1/pagos/intentos`, que sí exigen la cabecera: esos dos *crean* un
recurso nuevo cada vez que se llaman — sin un estado previo que la
transición pueda rechazar, no hay forma de que el propio dominio detecte un
reintento por su cuenta.

## Reglas que el backend nunca delega al cliente

1. Recalcular precio, IVA y total.
2. Verificar existencias.
3. Decidir si un método de pago está disponible para ese destino y ese monto.
4. Decidir si un pago está aprobado. La verdad es la consulta a la pasarela, no
   el parámetro que trae el navegador al volver.
