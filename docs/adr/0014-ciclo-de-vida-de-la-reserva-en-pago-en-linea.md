# ADR 0014. Ciclo de vida de la reserva de inventario en pago en línea

Fecha: 2026-09-03. Estado: aceptada.

## Contexto

`docs/02-modelo-datos.md` ya decía, desde antes de esta fase: "el pago
aprobado convierte reserva en salida; el pago rechazado o vencido la libera."
Hasta el cierre de esta fase eso era una intención, no código:
`AplicadorDeResultadoDePago` transicionaba el `Pedido`, pero nunca llamaba a
`Inventario.confirmar` ni a `Inventario.liberar`. Un pedido `PAGADO` no
convertía su reserva en salida real, y uno `PAGO_FALLIDO` no la liberaba —
solo quedaba libre cuando la reserva vencía sola, a los 30 minutos.

Cerrar ese hueco chocó con una funcionalidad ya construida en la misma fase:
`ReintentarPago`, que hasta entonces solo cambiaba el estado del pedido de
`PAGO_FALLIDO` a `PAGO_PENDIENTE`, sin tocar inventario. Si `PAGO_FALLIDO`
pasaba a liberar la reserva de inmediato (tal como pide el documento),
`ReintentarPago` se quedaba sin nada que confirmar si el segundo intento se
aprobaba.

## Decisión

**Un pago rechazado o con error libera la reserva de inmediato**, siguiendo
`docs/02-modelo-datos.md` al pie de la letra — no se espera a que venza por
tiempo. **`ReintentarPago` re-reserva de verdad**: revalida existencia real
por línea y crea una reserva nueva (`Inventario.reservar`,
`Pedido.actualizarReservas` reemplaza el `idReserva` de cada línea sin tocar
precio, nombre ni cantidad). Si el stock ya no alcanza — se vendió mientras
tanto — el reintento falla con `ExistenciaInsuficienteException`: un caso de
negocio real, no un error del sistema.

**Un pago aprobado confirma la reserva** (`Inventario.confirmar`, la
convierte en salida real). Caso límite: si la reserva ya venció por tiempo o
ya se había resuelto antes (un webhook tardío, o la conciliación programada
llegando después de los 30 minutos de la reserva), no se confirma a ciegas —
la unidad pudo haberse vendido a otro comprador ya. El pago y el pedido se
actualizan igual (la plata ya entró, eso no se revierte), pero el resultado
se marca con `ResultadoEventoDePago.APLICADO_SIN_CONFIRMAR_INVENTARIO`
(`log.error` en el webhook, sin bloquear su respuesta 200) para revisión
manual, en vez de arriesgar una sobreventa silenciosa o rechazar un pago ya
cobrado.

## Alternativas

**No liberar en `PAGO_FALLIDO`, solo al vencer por tiempo o al aprobar**: más
simple, no exige tocar `ReintentarPago` ni `Pedido`. Se descartó porque se
aparta de la frase literal de `docs/02-modelo-datos.md`, y porque bloquea
stock hasta 30 minutos completos aunque ya se sepa que ese intento falló — un
comprador legítimo detrás en la fila no podría comprar la última unidad
mientras tanto.

**Rechazar el evento aprobado sobre una reserva vencida** en vez de marcarlo
y seguir: se descartó porque el dinero ya entró a la cuenta del negocio —
negarse a reconocer el pago no lo revierte, solo esconde el problema real
(posible sobreventa) detrás de un pago fantasma sin pedido pagado.

## Consecuencias

Un pedido puede reintentar su pago un número indefinido de veces, cada una
re-reservando inventario — no se puso un tope. Si eso resulta ser un problema
operativo (reservas repetidas bloqueando stock sin comprar nunca), se revisa
aparte.

`ResultadoEventoDePago.APLICADO_SIN_CONFIRMAR_INVENTARIO` hoy solo se ve en
los logs del backend (`log.error`) — no hay ninguna vista en el panel que
liste estos casos para seguimiento. Si en producción resulta ser más frecuente
de lo esperado, hace falta ese seguimiento, igual que existe para el recaudo
pendiente.
