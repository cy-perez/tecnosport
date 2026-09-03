-- LineaPedido guarda el id del movimiento RESERVA que la reservó (docs/02-modelo-datos.md): sin
-- este id no hay forma segura de saber cuál reserva liberar cuando el pedido se rechaza en la
-- entrega o el pago falla — dos pedidos distintos pueden tener reservas pendientes de la misma
-- variante al mismo tiempo, no se puede adivinar por variante y cantidad.
alter table linea_pedido add column id_reserva uuid not null;
