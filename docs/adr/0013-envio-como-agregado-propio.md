# ADR 0013. `Envio` como agregado propio, con el recaudo aparte

Fecha: 2026-09-03. Estado: aceptada, **ampliada por `adr/0022`**.

## Contexto

Al construir el despacho de contraentrega (transportadora, guía, costo real) y
su conciliación de recaudo (comisión de la transportadora, fecha), hacía falta
decidir dónde vive ese dato: como campos sueltos dentro de `Pedido`, o como su
propio agregado.

`docs/02-modelo-datos.md` ya listaba `Envio` como entidad principal
("transportadora, guía, estado, costo real, recaudo"), pero eso era una
intención sin construir todavía; no había puerto ni tabla.

## Decisión

`Envio` es un agregado propio, en su propia tabla (`envio`, con FK a
`pedido`), no campos en `Pedido`. Nace en el despacho
(`DespacharPedido`) con transportadora, guía y costo de envío real; la
comisión de recaudo y su fecha de conciliación llegan después, con
`Envio.conciliarRecaudo`, y quedan nulas hasta entonces.

`Envio` no tiene un campo `estado` propio como sugería la tabla original de
`docs/02-modelo-datos.md`: el estado del recaudo (pendiente, conciliado) lo
sigue llevando `Pedido.estado` (`RECAUDO_PENDIENTE`, `RECAUDO_CONCILIADO`),
igual que ya lleva el resto del ciclo del pedido. Duplicar el estado en dos
agregados habría abierto la puerta a que se desincronizaran.

`RepositorioEnvios` es un puerto nuevo y separado de `RepositorioPedidos`, con
`guardar` y `buscarPorPedidoId` — lo mínimo que los casos de uso de despacho y
conciliación de recaudo necesitan hoy.

## Alternativas

Campos sueltos en `Pedido` (`transportadora`, `guia`, `costoEnvio`,
`comisionRecaudo`, `recaudoConciliadoEn`): menos piezas nuevas de entrada
(sin agregado ni repositorio aparte), pero aleja el modelo de datos del que ya
documentaba `docs/02-modelo-datos.md`, y mezcla en `Pedido` datos que no son
parte de lo que el comprador acordó (precio, líneas, dirección) con datos
operativos de logística que solo existen para pedidos despachados.

## Consecuencias

Un pedido que nunca se despacha (por ejemplo, uno en `PAGO_FALLIDO`
definitivo) nunca tiene fila en `envio` — no hace falta una columna nullable
en `pedido` para un dato que la mayoría de los pedidos no tendrán en la mitad
de su ciclo de vida.

La respuesta de la API de pedido (`PedidoRespuesta`) **todavía no expone**
los datos de `Envio` — se pueden escribir (`/despacho`, `/recaudo`) pero no
leer por ningún endpoint todavía. Queda como pendiente explícito para cuando
se retome el panel administrativo (`docs/09-plan-de-arranque.md`).

## Ampliación (2026-09-08)

`adr/0021` y `adr/0022` le agregan a este agregado la tarifa cotizada (con su
identificador de Skydropx, transportadora, servicio, valor cobrado, plazo
estimado y vencimiento) y la colección de `EventoSeguimiento`.

La decisión de fondo no cambia y se confirma: `Envio` sigue **sin `estado`
propio**. Los estados de la transportadora viven en sus eventos, y el estado del
pedido lo sigue llevando `Pedido.estado`. Si se hubieran puesto los doce estados
de Skydropx como un campo de `Envio`, habría dos máquinas de estados que
sincronizar y un vocabulario de proveedor incrustado en el modelo.
