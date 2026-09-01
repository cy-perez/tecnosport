# ADR 0006. Pago contraentrega habilitado con reglas de servidor

Fecha: 2026-08-30. Estado: aceptada.

## Contexto
Inicialmente se descartó el contraentrega. Se decide habilitarlo, porque en
Colombia sigue siendo el método que desbloquea al comprador que no confía en pagar
por internet, y para ropa y bolsos es una porción real de las ventas.

El riesgo es concreto: mercancía despachada sin cobrar, rechazos en la entrega,
dinero que llega días después a través de la transportadora, y equipos de alto
valor viajando sin garantía de pago.

## Decisión
Se habilita, con estas condiciones, todas decididas en el servidor:

- Cobertura por ciudad de destino, según lo que cubra la transportadora con
  recaudo.
- Monto máximo configurable.
- Categorías excluibles por configuración.
- Compradores con rechazo previo registrado no vuelven a ver el método.
- Verificación por contacto antes de despachar, registrada.
- Estados propios de recaudo pendiente y conciliado en el ciclo del pedido.
- Reserva de inventario sin vencimiento por tiempo, liberada al cancelar o al
  rechazar en la entrega.

## Alternativas
No habilitarlo: pierde ventas reales. Habilitarlo sin restricciones: expone el
negocio a pérdida directa, especialmente en celulares.

## Consecuencias
El ciclo del pedido se vuelve más largo y con más estados. Aparece una tarea
operativa nueva, la conciliación del recaudo, que el panel tiene que hacer
visible. La comisión de recaudo se registra como costo real para que el margen
por pedido no sea una estimación optimista.
