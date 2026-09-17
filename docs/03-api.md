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
POST /api/v1/envios/cotizacion              costo de envío y plazo para este carrito y destino
POST /api/v1/pedidos/metodos-de-pago-disponibles   qué métodos ofrece el negocio hoy y aplican a este carrito y destino
POST /api/v1/pedidos                        revalida precios, existencias y costo de envío, reserva
POST /api/v1/pagos/intentos                 crea el intento en la pasarela
PATCH /api/v1/pagos/intentos/{referencia}   registra el id de transacción de Wompi al volver del checkout
POST /api/v1/pagos/webhook                  eventos de Wompi, firma verificada
POST /api/v1/envios/webhook                 eventos de seguimiento de Skydropx, firma verificada
POST /api/v1/pedidos/{id}/reintentar-pago   PAGO_FALLIDO -> PAGO_PENDIENTE
GET  /api/v1/pedidos/{id}/seguimiento       con token del correo, sin sesión
```

`GET /api/v1/envios/cobertura` **se retira**: la cobertura de contraentrega ya no
sale de una tabla propia sino de la cotización (`ADR-0023`), así que la respuesta
de `/metodos-de-pago-disponibles` es el único lugar donde el cliente se entera de
si hay contraentrega para ese destino.

`POST /api/v1/pedidos` **vuelve a hacer las dos preguntas** que ya respondió
`/metodos-de-pago-disponibles`, porque no se fía de que el cliente las haya hecho
(regla dura #7). Si el método existe pero el negocio no lo ofrece hoy —la cuenta
de la pasarela no lo tiene activado, `WOMPI_METODOS_HABILITADOS`— responde `409`
con `codigo: "METODO_DE_PAGO_NO_HABILITADO"`; si es contraentrega y no aplica a
ese destino y ese monto, `409` con `codigo: "CONTRAENTREGA_NO_DISPONIBLE"`. Son
`409` y no `400` porque la petición está bien formada y el método existe: lo que
cambió es qué se acepta, y pudo cambiar entre las dos llamadas (`ADR-0029`).

### Cotización de envío

`POST /api/v1/envios/cotizacion` recibe **las líneas del carrito** —variante y
cantidad— y el destino, y devuelve **una** opción, la más económica que cubre el
destino:

```json
{
  "costoEnvio": { "valor": 14900, "moneda": "COP" },
  "transportadora": "Coordinadora",
  "diasEstimados": 3,
  "venceEn": "2026-09-09T14:05:00Z"
}
```

- **Líneas y no el id del carrito**, decidido al construirlo el 11 de septiembre
  de 2026. Es lo mismo que reciben `POST /api/v1/pedidos` y
  `/pedidos/metodos-de-pago-disponibles`, que el checkout llama en el mismo paso:
  mandar `carritoId` a uno y `lineas` al otro obligaba al frontend a hablar dos
  idiomas para la misma pantalla. No afloja la regla dura #7 — el peso, las
  medidas y el precio los sigue resolviendo el servidor contra el catálogo, y lo
  único que el cliente elige es qué variantes cotizar.
- **`transportadora` es el nombre para mostrar** tal como lo da el proveedor
  ("Coordinadora", "Inter Rapidísimo"), no un código. Es un nombre propio: no se
  traduce y no pasa por Transloco.
- **`diasEstimados` en cero significa sin estimado**, no "llega hoy". Hay tarifas
  que no declaran plazo y no se les inventa uno.
- **No dice nada de contraentrega.** Llevó un `admiteContraentrega` que era
  estructuralmente falso siempre: esta cotización se pide **sin** recaudo —el
  comprador todavía no ha elegido cómo paga— y la cobertura de recaudo solo se
  sabe pidiéndola con recaudo. Un booleano que no puede ser cierto engaña al
  siguiente que lo lea. Quien lo necesite pregunta a
  `/pedidos/metodos-de-pago-disponibles`, que es donde este documento ya decía
  que se resuelve.
- **Tiene límite de peticiones por IP**, a diferencia del resto de endpoints
  públicos de lectura: es el único que llama sincrónicamente a un proveedor
  externo con cuota y que se paga.
- **Una tarifa vencida no se ofrece.** Skydropx deduplica cotizaciones por
  contenido y puede devolver la de ayer, con su vencimiento original, al mismo
  carrito y el mismo destino.

- **`POST` y no `GET`** aunque no cree nada persistente para el cliente: el cuerpo
  lleva el carrito y la dirección, y una dirección de entrega no va en una URL que
  se registra en los logs del balanceador.
- **No devuelve la lista de tarifas ni el `rate_id`.** El servidor elige la más
  económica (`ADR-0021`) y el identificador del proveedor es interno: si viajara
  al navegador, alguien podría devolverlo alterado al crear el pedido.
- **Cuando no hay tarifa, no es un error del sistema.** Responde `409` con
  `codigo: "ENVIO_SIN_COBERTURA"` y el checkout ofrece solo la recogida en el
  punto. Un `502` sería mentir sobre de quién es el problema; el destino
  simplemente no se puede despachar hoy.
- **Y cuando no se pudo cotizar, es otra cosa: `503` con `codigo:
  "COTIZACION_NO_DISPONIBLE"`.** No es lo mismo "a esta dirección hoy no llega
  nadie" —que le pide al comprador cambiar la dirección— que "no pudimos
  preguntar" —que le pide reintentar—. Hasta el 16 de septiembre de 2026 los dos
  viajaban como `ENVIO_SIN_COBERTURA`, y eso mandaba a corregir direcciones que
  estaban bien: medido, la primera cotización de un contenido nuevo se pasa de la
  ventana de sondeo y el reintento la trae en 1,6 s, porque Skydropx deduplica por
  contenido (`docs/13` §6.9).
  Entran por aquí los cuatro motivos técnicos —sin credenciales, proveedor no
  disponible, respuesta inesperada y sondeo agotado—, que se distinguen en el
  registro y no en la respuesta: al comprador se le dice lo mismo en los cuatro y
  publicar la forma en que falla un proveedor no le sirve a nadie.
  **El cliente puede reintentar**, y el checkout ya lo hace una vez. Lo que no
  cambia es el criterio *fail-closed*: sin tarifa no se inventa un flete, y la
  recogida en el punto sigue disponible.
- **La cotización se repite en el servidor al crear el pedido.** Lo que el cliente
  recibió es informativo; el costo que se cobra lo fija `POST /api/v1/pedidos`
  (regla dura #7). Si entre las dos llamadas la tarifa cambió, manda la del
  pedido, y por eso el resumen se vuelve a pintar con lo que devuelve el pedido.
- **`RETIRO_EN_PUNTO` no cotiza.** El checkout no llama este endpoint cuando el
  comprador elige recogida: no hay destino, y el costo es cero por definición.

### Webhook de seguimiento

`POST /api/v1/envios/webhook` recibe los eventos de Skydropx, **verifica la firma
antes de aplicar nada** y responde siempre `200`, incluso cuando descarta el
evento (firma inválida, guía desconocida) — reintentar no arregla ninguna de las
dos cosas. Es idempotente por identificador de evento, o por el hash de la firma
si el proveedor no manda uno propio, exactamente como el de Wompi.

`GET /api/v1/pedidos/{id}/seguimiento` gana transportadora, guía, plazo estimado
y la lista de eventos. No expone el identificador de tarifa, el costo real del
flete ni la comisión de recaudo: eso es margen (`docs/11-pagos-y-envios.md`).

### Filtros, orden y paginación de `GET /api/v1/productos`

| Parámetro | Qué hace |
|---|---|
| `categoria` | slug de la categoría |
| `marca` | id de la marca |
| `linea` | `ROPA_Y_CALZADO`, `BOLSOS` o `TECNOLOGIA` |
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
POST /api/v1/admin/productos/{id}/imagen-principal            confirma la subida, reemplaza la principal y borra la anterior del bucket
GET /api/v1/admin/pedidos                                   paginado; ?estado= filtra y ordena por más antiguo primero
POST /api/v1/admin/pedidos/{id}/verificar-contraentrega     contacto por WhatsApp o llamada
POST /api/v1/admin/pedidos/{id}/emitir-guia                 le pide las guías a Skydropx; 202, no despacha todavía
POST /api/v1/admin/pedidos/{id}/despacho                    registra una guía emitida por fuera y despacha
POST /api/v1/admin/pedidos/{id}/entrega                     marca entregado
POST /api/v1/admin/pedidos/{id}/rechazo-entrega             libera inventario, registra motivo
POST /api/v1/admin/pedidos/{id}/recaudo                     concilia contraentrega
POST /api/v1/admin/pedidos/{id}/conciliar-transferencia     concilia transferencia manual

GET /api/v1/admin/pedidos/{id}/retractos            las solicitudes de retracto del pedido, la más nueva primero
POST /api/v1/admin/pedidos/{id}/retractos           radica un retracto ejercido por correo o WhatsApp
POST /api/v1/admin/retractos/{id}/recepcion         el producto volvió: pedido a DEVUELTO e inventario de vuelta
POST /api/v1/admin/retractos/{id}/reembolso         deja la constancia del dinero devuelto y cierra la solicitud

GET /api/v1/admin/envios/revision                   lo que pide ojo humano: guías quietas y emisiones sin desenlace
POST /api/v1/admin/envios/revision/guias/{numero}/acuse      deja constancia de que alguien miró esa guía
POST /api/v1/admin/envios/revision/emisiones/{id}/acuse      lo mismo para una emisión con saldo comprometido

POST /api/v1/admin/sets-rotacion                    abre un set vacío en BORRADOR
POST /api/v1/admin/sets-rotacion/{id}/subidas       N URL firmadas, una por fotograma
POST /api/v1/admin/sets-rotacion/{id}/completar     verifica los objetos y pasa a COMPLETO
POST /api/v1/admin/sets-rotacion/{id}/publicar      de COMPLETO a PUBLICADO: la ficha muestra el visor
DELETE /api/v1/admin/sets-rotacion/{id}             borra el set y sus objetos del bucket
```

**La bandeja de revisión junta dos cosas que se atienden distinto** y por eso
viajan en dos listas, no mezcladas: guías cuyo último movimiento las dejó quietas
—los cinco estados de `EstadoEnvio.exigeRevisionManual()`— y emisiones en
`INDETERMINADA` o `PARCIAL`, donde hay saldo comprometido. Van en la misma
respuesta porque para quien atiende son una sola pregunta: qué paquete necesita
que alguien haga algo.

De cada guía viajan **dos fechas**, `ocurrioEn` y `recibidoEn`, y no es
redundancia: la segunda es la nuestra, y es contra la que el servidor compara el
acuse. Un evento que *ocurrió* antes del acuse pero que llegó después sigue siendo
algo que nadie ha visto. De cada emisión viaja el `idTarifa`, que es lo único con
lo que se puede hacer algo: es la llave con la que el panel de la plataforma
encuentra el envío.

**El acuse no resuelve nada.** Deja escrito quién miró, cuándo y qué concluyó, y
con eso la fila sale de la bandeja; si a una guía le llega un evento posterior al
acuse, vuelve sola. Una emisión `INDETERMINADA` acusada **sigue abierta** y sigue
bloqueando una emisión nueva de ese pedido: decidir que no hubo cobro y pasarla a
`FALLIDA` mueve plata y todavía no tiene endpoint. Acusar algo que no está pidiendo
revisión responde 409, para que no quede escrito un problema que nunca existió.

**El retracto lo radica el negocio, no el comprador**, y por eso sus rutas están
bajo `/admin`: el canal que los términos publicados prometen es el correo y
WhatsApp (Ley 1480 de 2011, art. 47), así que estos endpoints dejan constancia de
un acto que ocurre por fuera del sitio. Radicar cuelga del pedido porque una
solicitud no existe sin él; los dos pasos siguientes cuelgan de la solicitud, que
es lo que avanza.

`verdictoAlRadicar` tiene **tres** valores: `EN_PLAZO`, `VENCIDO` e
`INDETERMINADO`. El tercero no es un estado de error — significa que pasó el
límite más temprano posible pero el calendario de festivos no está cargado, y sin
él afirmar que un plazo venció sería negarle un derecho a alguien que quizá está
a tiempo. Ninguno de los tres bloquea la radicación: decide una persona con el
dato delante.

`reembolso` **no mueve dinero**. Es el registro de un acto hecho por fuera —dos de
los tres métodos de pago se devuelven así por definición— y sirve para demostrar
el plazo de quince días calendario del reintegro, que corre desde
`productoRecibidoEn` y que el servidor devuelve ya calculado en
`limiteDeReintegro`.

**El comprador ve su retracto en `GET /pedidos/{id}/seguimiento`**, que desde
ahora devuelve `PedidoSeguimientoRespuesta` y no `PedidoRespuesta`. Son dos
records distintos a propósito: compartir uno solo entre el panel y el público fue
lo que dejó salir durante toda la fase 3 el costo real del flete y la comisión de
recaudo a cualquiera con un id de pedido y el correo correcto. La respuesta
pública lleva `EnvioPublicoRespuesta` —transportadora, guía y fecha de despacho, y
nada de dinero— y `RetractoPublicoRespuesta`, que deja fuera quién atendió la
solicitud y el veredicto de plazo: ese último puede valer `INDETERMINADO`, y
decirle a un comprador "fuera de plazo" es una afirmación jurídica que el sistema
no siempre puede sostener.

Sin `Idempotency-Key`: la máquina de estados de la solicitud ya hace idempotentes
estas acciones administrativas de un solo actor, y radicar dos veces lo bloquea
la guarda de "una sola en curso" con un 409.

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

**`emitir-guia` responde `202` y no `200`, y no mueve el pedido.** Lo que se acepta
es la solicitud, no el despacho: Skydropx cobra al crear el envío y devuelve la guía
en `null`, así que el pedido se queda en `EN_PREPARACION` y pasa a `DESPACHADO` cuando
una tarea programada trae los números — entre veinticinco segundos y varios minutos
(`adr/0033`). El cuerpo de la respuesta es la emisión en curso: con qué transportadora
salió y cuántos paquetes son, nunca los identificadores de la plataforma.

**Excepción a la cabecera `Idempotency-Key`:** las acciones que transicionan
un pedido *ya existente* a partir de su estado actual —`conciliar-transferencia`,
`verificar-contraentrega`, `despacho`, `entrega`, `rechazo-entrega`,
`recaudo` (las seis, de panel, rol `ADMIN`) y `reintentar-pago` (pública,
sin sesión, mismo modelo de confianza que crear el pedido)— no la usan,
aunque muevan dinero o inventario. Su idempotencia sale gratis de la propia
máquina de estados de `Pedido`: un segundo `POST` sobre un pedido que ya
transicionó cae en un estado que `EstadoPedido` no admite como destino y la
petición se rechaza con 422, sin duplicar nada ni necesitar una llave
aparte.

`emitir-guia` **no entra en ese argumento**, y por eso se protege aparte: no
transiciona el pedido, así que la máquina de estados no la frena y dos clics serían
dos cobros. Lo que la frena es un índice único parcial que impide dos emisiones
abiertas para el mismo pedido, y responde `409` a la segunda. La fila que lo sostiene
**se escribe antes de llamar a la plataforma**, así que el segundo clic choca cuando
todavía no hay nada que pagar (`ADR-0033`).

Sus códigos de error, y por qué no son el mismo:

| Código | Cuándo |
|---|---|
| `409 EMISION_NO_APLICABLE` | El pedido es de retiro en punto, no está en `EN_PREPARACION`, o le falta dirección o contacto |
| `409 EMISION_YA_EN_CURSO` | Ya hay una abierta. El mensaje distingue la que se resuelve sola en minutos de la **indeterminada**, que no se resuelve nunca sola: pudo crearse y cobrarse una guía, y hay que mirarlo en el panel de la transportadora antes de volver a emitir |
| `502` | La transportadora rechazó la emisión |
| `500` | Faltan nuestras credenciales. No es culpa de la transportadora y decirlo así mandaría a quien despacha a llamarlos por algo nuestro |

Y una nota de operación: mientras hay una emisión abierta, **el despacho a mano sigue
disponible**. Es la salida de quien está apurado, y la única cuando una emisión queda
indeterminada. La diferencia con `POST /api/v1/pedidos` y `POST
/api/v1/pagos/intentos`, que sí exigen la cabecera: esos dos *crean* un
recurso nuevo cada vez que se llaman — sin un estado previo que la
transición pueda rechazar, no hay forma de que el propio dominio detecte un
reintento por su cuenta.

## Reglas que el backend nunca delega al cliente

1. Recalcular precio, IVA y total.
2. Verificar existencias.
3. **Cotizar el envío y elegir la tarifa.** El costo de envío no se acepta del
   cliente en ninguna petición, y el identificador de tarifa del proveedor nunca
   sale del servidor.
4. Decidir si un método de pago se ofrece. Son dos preguntas: si el negocio lo
   ofrece hoy —lo que la cuenta de la pasarela tiene activado, `ADR-0029`— y si
   aplica a ese destino y ese monto, incluida la contraentrega, que desde
   `ADR-0023` depende de que la cotización traiga una tarifa con recaudo.
5. Decidir si un pago está aprobado. La verdad es la consulta a la pasarela, no
   el parámetro que trae el navegador al volver.
6. **Decidir si un envío se entregó.** La verdad es el webhook firmado del
   proveedor más la consulta de seguimiento, no lo que diga una pantalla.
