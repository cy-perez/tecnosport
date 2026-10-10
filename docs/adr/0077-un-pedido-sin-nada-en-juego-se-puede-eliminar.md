# ADR-0077: un pedido sin nada en juego se puede eliminar

Fecha: 2026-10-10
Estado: aceptada
Matiza: la regla "todo cambio de estado de un pedido queda registrado (...) No se sobrescribe
historia" de `docs/00-producto.md`, para los pedidos que cubre este ADR.

## Contexto

El 9 de octubre de 2026 se pidió un botón para eliminar un pedido desde el panel. Hasta hoy no
existía ningún borrado de pedidos: ni caso de uso, ni endpoint, ni método en el puerto. Lo que
había era cancelar, que cambia el estado y deja la historia.

Antes de decidir se comprobó:

- **La base solo borra en cascada las líneas y el historial** (`V4`). Pago, envío, emisión de
  guía, retracto, reintegro, PQR, garantía y reversión tienen llave hacia `pedido` sin cascada:
  cualquiera de ellos haría fallar el borrado.
- **La política de datos publicada** (`legales/es.json`, secciones de conservación y supresión)
  promete conservar los datos de una compra pagada por el término que impone la ley tributaria, y
  los necesarios para atender garantías mientras dure su término.
- **Un pago puede llegar tarde.** Wompi y Sistecrédito confirman después de que el pedido ya falló
  o se canceló; ese pago queda `APROBADO` y aparece en la bandeja de pagos sin pedido (`V81`).
- **`CREADO` nunca se guarda**: es el estado del constructor, y el pedido sale de él antes de
  existir en la base.

## Decisión

1. **Se elimina solo un pedido en `PAGO_FALLIDO` o `CANCELADO`** (`EstadoPedido.admiteEliminacion`).
   En los dos la reserva de inventario ya se liberó. Cualquier otro estado tiene dinero en camino,
   inventario apartado o un paquete que seguir: se cancela.
2. **Y solo si nada cuelga de él** (`BorradoDePedidos.compromisosDe`): ni un pago `APROBADO` o
   `PENDIENTE` —la pasarela todavía puede aprobarlo—, ni un envío o una emisión de guía, ni un
   retracto, un reintegro, una PQR, una garantía o una reversión. Un pedido cancelado después de
   cobrar tiene su pago y su reintegro, y por eso el estado solo no basta.
3. **Nunca uno de transferencia manual.** Su dinero no deja fila en `pago`: el comprobante llega
   por WhatsApp y conciliar solo mueve el estado. Una transferencia cancelada puede tener una
   consignación que nadie vio, y el sistema no tiene cómo saberlo. Lo encontró la revisión
   adversarial del 10 de octubre de 2026.
4. **Con la fila del pedido bloqueada**, como todo caso de uso que lo cambia, y con la regla
   repetida en el `where` de los dos `delete`: sin eso, un "reintentar pago" que confirma entre la
   lectura y el borrado dejaba un pago pendiente en Wompi con una referencia que ya no existe. Si
   algo entra igual, la llave de `pago` hace fallar el borrado y sale como `409`.
5. **Se borran con él sus líneas, su historial y sus intentos de pago fallidos** (con los avisos
   de la pasarela). Los movimientos de inventario se quedan: son la historia del stock y no
   apuntan al pedido con llave.
6. Rechazado, `409 PEDIDO_NO_ELIMINABLE`, y el panel sugiere cancelarlo. Hecho, el controlador deja
   un `warn` con el número del pedido y quién lo eliminó, que es lo único que queda.

## Consecuencias

**A favor:**

- Los pedidos de prueba y los que murieron en el pago se pueden limpiar desde el panel, sin SQL.
- Nada de lo que la ley o los textos publicados obligan a conservar se puede borrar por este
  camino: una compra pagada, un envío o un trámite bloquean el botón.

**En contra:**

- **El historial de esos pedidos desaparece.** La línea de registro dice quién y cuándo, pero no
  el detalle de las transiciones. Es lo que se pide para pedidos en los que no hubo venta.
- **El enlace del correo de pago fallido deja de funcionar.** Quien quisiera reintentar el pago
  encuentra "pedido no encontrado". El texto de la confirmación lo advierte.
- **La lista de tablas vive en el adaptador** (`BorradoDePedidosJdbc`). Una migración que agregue
  otra llave hacia `pedido` hará fallar el borrado con una violación de llave —el lado seguro—
  hasta que alguien decida a qué compromiso pertenece.
