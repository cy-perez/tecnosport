# ADR-0039 — Un rechazo del proveedor no es una caída

Fecha: 2026-09-18
Estado: aceptado
Relacionados: `adr/0021`, `adr/0035`, `adr/0036`, `docs/03-api.md`, `docs/13-skydropx-capacidades.md` §6.9

## Contexto

`ADR-0021` dejó el criterio *fail-closed* de la cotización: sin tarifa no se inventa un flete. Lo que
no dejó resuelto es **cómo se cuenta cada forma de no tener tarifa**, y el 16 de septiembre de 2026 ya
hubo que separar dos que estaban juntas: "no hay cobertura" —que le pide al comprador cambiar la
dirección— y "no pudimos preguntar" —que le pide reintentar—. Faltaba una tercera, y era la que más
caro salía.

**El adaptador contaba cualquier respuesta que no fuera 2xx como proveedor no disponible.** Cuando
Skydropx responde `422` con los campos que rechazó, eso era falso por dos lados a la vez:

- Le echaba la culpa a quien **sí** había contestado. En el registro quedaba "proveedor no
  disponible" de un proveedor que estaba arriba y había respondido en un segundo.
- Le pedía al comprador reintentar algo que **no puede funcionar**: Skydropx deduplica las
  cotizaciones por contenido, así que la misma pregunta trae el mismo rechazo. El comprador espera,
  vuelve a intentar, y se va.

No es hipotético: es exactamente la forma que tenía el valor declarado por debajo del mínimo antes de
`ADR-0035`. Un cable de 8.000 dentro de un pedido de 400.000 tumbaba la cotización entera, el
comprador leía "intenta más tarde", y del lado nuestro el registro decía que el proveedor no estaba
disponible. **Una venta que no ocurre y ningún error que la explique.** `ADR-0035` quitó esa causa;
no quitó la clase de fallo.

Y la mitad simétrica del defecto vivía en la emisión, al revés: ahí **todo** `4xx` se contaba como
datos rechazados, así que un token revocado mandaba a buscar un defecto en un pedido que estaba bien.

## Decisión

**El código de respuesta dice de quién es el problema, y eso decide qué se le dice a quien compra.**

Un rechazo del proveedor es su propio desenlace: motivo `DATOS_RECHAZADOS` en el puerto,
`CotizacionRechazadaException` en la aplicación y **`409 COTIZACION_RECHAZADA`** en la API.

`409` y no el `503` de "no se pudo cotizar", con el mismo criterio que sus dos hermanos de negocio
(`ENVIO_SIN_COBERTURA`, `ARTICULO_NO_ASEGURABLE`): la solicitud está bien formada, el servicio del
que dependemos está arriba, y **reintentar no lo arregla**. Un `503` le promete al cliente lo
contrario. El checkout lo trata como los otros dos: ofrece la recogida en el punto, con su propio
texto, sin invitar a un reintento imposible.

Las tres respuestas terminales se diferencian en lo único que le importa a quien lo lee: **quién
tiene que hacer algo.**

| Código | Quién lo arregla |
|---|---|
| `ENVIO_SIN_COBERTURA` | El comprador, cambiando la dirección |
| `ARTICULO_NO_ASEGURABLE` | Nadie: ese artículo no va a domicilio (`adr/0036`) |
| `COTIZACION_RECHAZADA` | **Nosotros** |

### No todo `4xx` es nuestro cuerpo

El mapeo es quirúrgico, y esta tabla es la decisión que evita repetir el defecto en el otro sentido:

| Respuesta | Motivo | Por qué |
|---|---|---|
| `401`, `403` | `SIN_CREDENCIALES` | Un despliegue mal configurado. Se mira Secret Manager, no el carrito |
| `408`, `429`, `5xx` | `PROVEEDOR_NO_DISPONIBLE` | Temporal de verdad: reintentar sirve |
| Otro `4xx` | `DATOS_RECHAZADOS` | La plataforma juzgó nuestro cuerpo |

Y solo **en la creación de la cotización**, que es el único paso donde la plataforma juzga lo que
mandamos. En el sondeo el cuerpo ya fue aceptado, así que un `4xx` de ahí no puede significar "nos
rechazaron los datos": lo único que cambia entre los dos pasos es el token. La misma tabla se aplicó
al `4xx` de la emisión, porque es una sola regla y estaba escrita dos veces de dos formas distintas.

### Y se registra en `error`, no en `warn`

Es el único de los cinco motivos que va en `error`. Los otros cuatro son el mundo —un proveedor caído,
un token mal puesto, una ventana de sondeo corta—; este es **un defecto nuestro que se cobra en ventas
que no ocurren**. Van los nombres de los campos que la plataforma rechazó y nunca sus valores: son el
teléfono y la dirección de quien compra (`docs/08-seguridad-legal.md`).

## Consecuencias

- El comprador deja de recibir una invitación falsa a reintentar, y el carrito cae a la recogida en
  el punto, que es la salida que de verdad tiene.
- Quien opera recibe una alarma con los campos culpables el día que el catálogo traiga una medida
  imposible, en vez de un "proveedor no disponible" que manda a mirar al sitio equivocado.
- `MetodosDePagoDisponibles` atrapa el rechazo junto a sus dos hermanas: antes, un cuerpo rechazado
  tumbaba esa consulta con un `503` y el comprador no se quedaba sin contraentrega — se quedaba sin
  lista de medios de pago.
- Un cliente de la API tiene un código más que distinguir. Es el precio de decir la verdad, y
  `docs/03-api.md` lo documenta con las tres preguntas que resuelve.
- **Lo que esto no arregla**: nada garantiza que el motivo sea legible desde fuera del registro. Si
  estos rechazos se vuelven frecuentes, el sitio donde tienen que aparecer es la bandeja de revisión,
  no un `grep` en Cloud Logging.
