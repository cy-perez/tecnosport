# ADR 0022. Seguimiento del envío: webhook de Skydropx, con conciliación programada

Fecha: 2026-09-08. Estado: aceptada.

## Contexto

Hasta ahora el estado del pedido después del despacho lo movía una persona desde
el panel: `DESPACHADO` con la guía escrita a mano, y después `ENTREGADO` o
`RECHAZADO_EN_ENTREGA` cuando alguien se enteraba. El comprador podía consultar
`GET /api/v1/pedidos/{id}/seguimiento`, que le decía el estado del pedido —no
dónde va el paquete.

Con Skydropx la guía la emite la plataforma (`POST /shipments` con la cotización
y la tarifa elegidas) y los movimientos del paquete llegan por webhook, con
estados propios de la plataforma: `created`, `picked_up`, `in_transit`,
`last_mile`, `delivery_attempt`, `delivered_to_branch`, `delivered`, `exception`,
`in_return`, `canceled`, `destroyed` y `retained`.

## Decisión

**`POST /api/v1/envios/webhook`**, público y con firma verificada antes de aplicar
nada, exactamente como el de Wompi: evento sin firma válida se descarta y se
registra. Skydropx firma con HMAC sobre el cuerpo usando el secreto del webhook
que se configura en el panel. ~~`TODO: confirmar el nombre exacto de la cabecera
de firma y el algoritmo (la documentación pública menciona HMAC SHA-512) contra
la cuenta real antes de implementar.`~~ **Confirmado el 14 de septiembre de 2026
en la documentación oficial** (`docs/13-skydropx-capacidades.md`, §6.1): cabecera
`Authorization` —nombre configurable en el panel—, formato `HMAC <firma>`,
HMAC‑SHA512 sobre los bytes crudos del cuerpo, hexadecimal en minúsculas. Queda
comprobarlo contra un evento real cuando la cuenta pueda emitir una guía. No se
codifica de memoria: es el mismo error que ya costó una sesión con el vector de
firma de Wompi.

**Implementado ese mismo día** en `VerificadorFirmaEnvioHmac`, con el algoritmo
probado contra los vectores del RFC 4231 y no contra sí mismo. Dos consecuencias
que este ADR no había previsto: hizo falta conectar `SKYDROPX_SECRETO_WEBHOOK`
—que `docs/07-infra-gcp.md` listaba y nadie había cableado—, y el controlador pasó
a recibir el cuerpo como `byte[]`, porque un `String` deja la codificación en
manos del convertidor de Spring y el HMAC es sobre bytes. Mientras el secreto sea
el marcador de desarrollo, el endpoint sigue descartando todo: lo que falta ya no
es saber cómo verificar, sino con qué.

**Idempotente por identificador de evento**, y si Skydropx no manda uno propio,
por el hash de la firma — el mismo truco que resolvió el webhook de Wompi. Un
reintento del mismo evento tiene que ser inofensivo.

**El webhook responde siempre 200**, incluso cuando descarta el evento (firma
inválida, guía desconocida). Reintentar no arregla ninguna de las dos cosas y
entrar en el ciclo de reintentos de la plataforma solo agrega ruido.

**Los eventos se guardan todos, en orden, y no se sobrescriben.** `Envio` gana
una colección de `EventoSeguimiento` (estado de la plataforma, descripción,
momento del evento, momento de recepción). Es lo mismo que hace
`HistorialPedido` con las transiciones y por el mismo motivo: el día de la
reclamación hay que poder decir qué se supo y cuándo.

**Solo tres estados de Skydropx mueven el pedido:**

| Estado de Skydropx | Efecto en `Pedido` |
|---|---|
| `picked_up` | Confirma `DESPACHADO` si el pedido no lo estaba ya |
| `delivered` | `ENTREGADO`, y encadena `RECAUDO_PENDIENTE` si es contraentrega |
| `in_return` | `RECHAZADO_EN_ENTREGA`, libera inventario y registra el motivo |

Los demás —`in_transit`, `last_mile`, `delivery_attempt`,
`delivered_to_branch`, `retained`, `exception`, `canceled`, `destroyed`— se
registran como eventos y **no cambian el estado del pedido**. Duplicar en
`EstadoPedido` la máquina de estados de la transportadora acoplaría el grafo del
pedido al vocabulario de un proveedor, y ese grafo es la parte del dominio que
más caro sale mover. `exception`, `retained`, `canceled` y `destroyed` además
generan una alerta para revisión manual: son los casos en los que el paquete se
queda quieto y nadie lo nota hasta que reclama el comprador.

**El webhook no es la única verdad.** `TareaConciliacionEnvios`, tercera tarea
programada del proyecto y con el patrón de `TareaConciliacionWompi`, consulta
~~`GET /shipments/tracking/{guia}/{transportadora}`~~
`GET /api/v1/shipments/tracking?tracking_number=…&carrier_name=…` (la forma real,
medida el 15 de septiembre) para los envíos despachados sin evento reciente, y
aplica el resultado con la misma lógica que el webhook, en un componente
compartido. Los webhooks se pierden; un paquete entregado hace cinco
días con el pedido en `DESPACHADO` es un retracto que empieza a correr sin que el
sistema lo sepa.

**El comprador ve el rastro, no las tripas.** `GET /api/v1/pedidos/{id}/seguimiento`
—que ya existe y no exige sesión, con el correo hacienda de token— gana
transportadora, número de guía, plazo estimado y la lista de eventos. **No** expone
el identificador de tarifa, el costo real del flete ni la comisión de recaudo: eso
es información interna de margen.

**Las notificaciones las manda TecnoSport, no Skydropx.** La plataforma puede
avisarle al comprador por WhatsApp y por correo; no se activa. Los correos
transaccionales ya existen (`EnviadorDeCorreo`) y activar el canal del proveedor
significaría ampliar el tratamiento de sus datos a una finalidad publicitaria de
un tercero sin autorización específica, además de dos remitentes distintos
diciendo cosas parecidas. `[[ CONFIRMAR CON EL NEGOCIO: si se quiere que
Skydropx notifique por WhatsApp, hay que declararlo en la política de datos y
pedir autorización aparte. ]]`

## Consecuencias

`DespacharPedido` deja de recibir la guía escrita a mano: la pide a Skydropx y la
guarda. **Corregido el 16 de septiembre**: entre pedirla y tenerla hay minutos, y
puede no haberla nunca; ver la corrección al final. La acción del panel sigue
existiendo —alguien decide cuándo se despacha— pero ya no transcribe datos de
otra pantalla.

Aparece un tercero que recibe nombre, teléfono y dirección de entrega de cada
comprador, y que emite eventos sobre él. Eso cambia la política de datos y el
análisis de transferencia internacional (`docs/08-seguridad-legal.md`,
`docs/12-legales-de-envio.md`).

El seguimiento visible cierra, de paso, un hueco que no era de logística: el
plazo de entrega prometido y el retracto se cuentan **desde la entrega**, y hasta
ahora esa fecha dependía de que alguien la marcara a mano.

## Corrección del 16 de septiembre de 2026, medida contra el sandbox

Este ADR se escribió con la documentación por delante y sin una guía. Ya hay
tres emitidas y un ciclo de seguimiento capturado entero
(`docs/13-skydropx-capacidades.md`, §6.3, §6.6 y §6.7), y seis de sus
afirmaciones necesitan enmienda. Lo confirmado también se anota: **los doce
estados son exactamente los doce del enum de Skydropx, en el mismo orden**, y
`EstadoEnvio` coincide uno a uno.

**1. `DespacharPedido` no puede pedir la guía y guardarla en el mismo paso.**
`POST /shipments` responde `202` **sin número de guía**: el envío queda en un
estado no terminal —`in_progress`, `pending` o `creation_waiting`— mientras la
transportadora contesta. La emisión del 16 de septiembre tardó 2 min 22 s en
llegar a `success`. Y puede no llegar: tres emisiones de una misma noche
terminaron en `workflow_status: error` minutos después del `202`, con el saldo
reembolsado y el motivo en `error_detail` —un `500` de la transportadora, no un
cuerpo mal armado—. Así que **el pedido no se marca despachado con la respuesta
de creación**, y hace falta una rama para el `error` que lo devuelva a la cola en
vez de dejarlo con una guía que no existe y que nadie va a recoger. El estado
terminal se puede esperar releyendo el envío o por el webhook; cuál de los dos es
parte de escribir el despacho, y no se decide aquí.

**2. El endpoint de la conciliación no tiene esa forma.** Lo que este ADR escribió
como `GET /shipments/tracking/{guia}/{transportadora}` —y así lo lista también la
documentación— responde de verdad como
**`GET /api/v1/shipments/tracking?tracking_number=…&carrier_name=…`**, con los dos
datos en la consulta y no en la ruta. Es la forma que devolvió los cuatro eventos
del ciclo capturado. Gana lo medido.

**3. Los eventos no vienen como este ADR supuso.** Cuatro cosas que solo se ven
mirando eventos reales, y las cuatro rompen un lector ingenuo:

- **Llegan del más nuevo al más viejo.** Guardarlos "todos, en orden" exige
  invertir la lista, no confiar en el orden de llegada.
- **`description` y `event_description` llegan vacíos —`null` y `""`— justo en
  `picked_up` y en `delivered`**, que son dos de los tres estados que mueven el
  pedido. Un lector que exija texto se cae en el evento más importante.
- **`location` llegó `null` en los cuatro eventos.** No se puede mostrar.
- **`created` no genera evento.** Está en el enum y el paquete pasa por ese
  estado, pero el rastreo empieza en `picked_up`: la lista de eventos no es la
  historia completa del paquete, y el comprador no debería leerla como tal.

**4. Un pedido puede generar varias guías, y `Envio` guarda una.** Ninguna
transportadora colombiana de la cuenta admite multipaquete
—`multi_packages_enabled: false` en los siete servicios— y con dos bultos la
cotización cambia a `shipment_creation_type: multishipment` y cobra el doble. Con
la regla de "un bulto por variante", **un pedido de dos variantes son dos guías,
cada una con su número, su cobro y su propio hilo de eventos**. Este ADR asume una
guía por pedido de punta a punta. Las salidas visibles son tres —un `Envio` por
bulto, un `Envio` con varias guías, o consolidar en un solo bulto y perder las
medidas reales— y **no se elige aquí**: es la decisión que hay que tomar antes de
escribir el despacho.

**5. La recolección sigue siendo el tramo que falta, pero ya no a ciegas.** Solo
Coordinadora, Servientrega e Inter Rapidísimo recogen por API —lo dice el campo
`pickup` de cada tarifa—; 99 minutes y Envía solo por soporte. Programar exige el
envío en `success` y el barrio en la dirección de origen, el peso total en kilos
enteros, corte a las 12:00 y sin fines de semana. **`GET /pickups/coverage`
respondió `422` con mensaje vacío en cinco guías distintas**, así que hoy no se
pueden ofrecer fechas al que despacha: hay que proponer una ventana y que el
`422` diga si sirve. Falta programar una de verdad, y falta por saldo, no por
ignorancia (§6.7).

**6. La firma sigue sin comprobarse contra un evento real, y ya no es por falta
de guía.** La cuenta emitió tres y el ciclo se capturó **consultando por guía, no
recibiendo webhooks**: mientras `SKYDROPX_SECRETO_WEBHOOK` valga el marcador de
desarrollo, `VerificadorFirmaEnvioHmac` descarta todo lo que llegue. Lo que falta
es el secreto del panel y una URL pública a la que Skydropx pueda golpear, no
código.

**Y un dato para quien escriba el despacho:** `label_url` —el rótulo que alguien
tiene que imprimir— **apareció en la guía del 16 de septiembre** y no había
aparecido en la del 15, ni siquiera con el envío en `delivered`. No se sabe qué
lo decide. Un despacho que dé por hecho que el rótulo viene en la respuesta va a
fallar algún día sin avisar.
