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
que se configura en el panel. `TODO: confirmar el nombre exacto de la cabecera de
firma y el algoritmo (la documentación pública menciona HMAC SHA-512) contra la
cuenta real antes de implementar.` No se codifica de memoria: es el mismo error
que ya costó una sesión con el vector de firma de Wompi.

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
`GET /shipments/tracking/{guia}/{transportadora}` para los envíos despachados sin
evento reciente y aplica el resultado con la misma lógica que el webhook, en un
componente compartido. Los webhooks se pierden; un paquete entregado hace cinco
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
guarda. La acción del panel sigue existiendo —alguien decide cuándo se despacha—
pero ya no transcribe datos de otra pantalla.

Aparece un tercero que recibe nombre, teléfono y dirección de entrega de cada
comprador, y que emite eventos sobre él. Eso cambia la política de datos y el
análisis de transferencia internacional (`docs/08-seguridad-legal.md`,
`docs/12-legales-de-envio.md`).

El seguimiento visible cierra, de paso, un hueco que no era de logística: el
plazo de entrega prometido y el retracto se cuentan **desde la entrega**, y hasta
ahora esa fecha dependía de que alguien la marcara a mano.
