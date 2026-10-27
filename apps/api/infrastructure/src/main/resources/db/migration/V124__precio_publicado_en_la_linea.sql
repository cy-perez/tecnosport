-- El precio publicado en la linea del pedido (27 de octubre de 2026, ADR-0095).
--
-- `precio_unitario` sigue siendo lo que se cobra por unidad, y desde aqui depende del medio de
-- pago: el publicado si lo cobra una pasarela (Wompi, Sistecredito), el de contado si no
-- (transferencia, en el punto, contra entrega). `precio_publicado` congela el precio anunciado de
-- la variante al comprar, para que el descuento por pagar sin pasarela quede auditable:
-- (precio_publicado - precio_unitario) * cantidad.
--
-- Las lineas que ya existen se cobraron al unico precio que habia, que era el anunciado: el
-- publicado es el mismo y el descuento, cero.
alter table linea_pedido add column precio_publicado numeric(14, 2);

update linea_pedido set precio_publicado = precio_unitario;

-- Nullable a proposito, aunque la revision nueva siempre lo escribe: mientras dura el despliegue,
-- la revision vieja sigue atendiendo y crea pedidos sin conocer esta columna. Con `not null` cada
-- compra en esa ventana daba 500. Una linea sin publicado se lee como sin descuento (publicado
-- igual al cobrado), que es justo lo que cobro la revision vieja.
--
-- El publicado es el maximo que alguien paga: un precio cobrado por encima seria un recargo, que es
-- justo lo que el ADR-0095 descarto. El dominio lo exige al construir la linea; esto lo exige a
-- cualquier otra puerta.
alter table linea_pedido add constraint ck_linea_pedido_publicado_no_menor_que_cobrado
    check (precio_publicado is null or precio_publicado >= precio_unitario);
