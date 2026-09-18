# ADR-0038 — Anular la guía no puede tumbar la cancelación

Fecha: 2026-09-17
Estado: aceptado
Relacionados: `adr/0013`, `adr/0033`, `adr/0034`, `docs/11-pagos-y-envios.md`

## Contexto

Este ADR se escribe **después del código**, el 18 de septiembre de 2026, y eso es parte de lo que
tiene que quedar registrado: la decisión se tomó, se implementó y se revisó sin ADR, y el cierre de
la fase la encontró sin documento. El código y el mensaje del commit la explican bien; lo que faltaba
es que se pueda encontrar sin leer un `git log`.

**El agujero era alcanzable desde el panel, no un caso raro.** La guía se emite estando el pedido
`EN_PREPARACION`, y `EN_PREPARACION → CANCELADO` es una transición válida. `CancelarPedido` no tocaba
`Envio` ni `EmisionDeGuia` en ninguna línea, así que cancelar un pedido con la guía ya pedida dejaba
una **guía viva y cobrable de un pedido que ya no existe**: un paquete que la transportadora recoge,
entrega y factura sin que nadie lo note hasta el extracto.

La plataforma sí sabe anular — `POST /api/v1/shipments/{id}/cancellations` — pero eso abre la
pregunta que este ADR responde: ¿qué pasa con la cancelación del pedido si la anulación falla?

## Decisión

**El pedido se cancela igual.** Anular la guía es un efecto de la cancelación, nunca su condición:
si la plataforma se niega, no contesta, o la emisión nunca devolvió identificadores con los que
pedirlo, el pedido queda cancelado, el inventario vuelve y el reintegro se registra exactamente
igual.

La razón es de daño comparado, y es la misma que ordena todo lo demás en este módulo: **un comprador
sin su plata porque un proveedor no contestó es peor que una guía huérfana que alguien anula a
mano.** La primera se le nota al comprador de inmediato y no tiene salida desde nuestro lado; la
segunda la resuelve una persona en el panel de Skydropx en dos minutos, y lo único que hace falta es
que se entere.

### Y por eso el fallo tiene que llegar al dominio

`application` no tiene registro de logs por diseño, así que un fallo de anulación no tiene dónde
esconderse: o se guarda como un hecho, o se pierde. Se guarda, con dos estados de `EstadoEmision`:

| Estado | Qué significa |
|---|---|
| `ANULADA` | Se pidió la anulación y la plataforma la aceptó. Final. |
| `SIN_ANULAR` | Se canceló el pedido y **al menos una guía pudo quedar viva**. Final, y pide ojo humano. |

`SIN_ANULAR` **aparece en la bandeja de revisión sin que la bandeja aprenda nada nuevo**, porque esa
pantalla pregunta por `EstadoEmision.exigeOjoHumano()` y no por una lista de nombres (`adr/0034`).
Es el tercer estado que entra por esa puerta, después de `INDETERMINADA` y `PARCIAL`, y por el mismo
motivo que los otros dos: hay plata de por medio que un programa no puede desenredar.

Los dos estados quedan fuera de `resuelta()` a propósito. Aquello responde "¿la plataforma va a decir
algo más de esta emisión?" y lo usa `EmisionDeGuia.resolver`, que exige partir de una emisión
abierta; anular ocurre **después**, sobre una emisión ya resuelta, y meterlo ahí dejaría pasar un
`resolver` que convierte una guía emitida en anulada sin haber llamado a nadie.

### Un 422 al anular se cuenta como anulada

La plataforma responde `422 "El envío no se puede cancelar"` cuando ya no hay nada que cancelar —el
envío murió solo, o ya iba en camino—. Se cuenta como `ANULADA` y no como `SIN_ANULAR`: lo que
`SIN_ANULAR` existe para vigilar es una guía que alguien tiene que ir a matar a mano, y un envío que
la plataforma declara no cancelable no se arregla insistiendo. Lo que se pierde con esa elección está
escrito en `ResultadoCancelacion`: una guía que ya iba en camino se cuenta como anulada.

## Consecuencias

- Cancelar un pedido despachado deja de ser un agujero de dinero silencioso, que era el hueco
  funcional más grande que quedaba de la integración.
- Aparece un estado terminal que **no** se reintenta solo. Es deliberado: reintentar una anulación en
  un lote programado dejaría la guía viva más tiempo sin que nadie mirara, y el aviso de la bandeja
  ya consigue que alguien mire dentro del día.
- El pedido cancelado no espera al proveedor, así que la cancelación sigue siendo rápida para quien
  la pide desde el panel.
- Queda pendiente lo de siempre en este módulo: nada mide cuánto tarda el negocio en atender lo que
  la bandeja muestra.
