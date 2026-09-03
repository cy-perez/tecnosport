-- Envio: transportadora, guía y costo real del despacho (docs/02-modelo-datos.md). Nace en el
-- despacho; el recaudo de contraentrega (comisión, monto conciliado) llega con su propia columna
-- cuando se construya esa conciliación.
create table envio (
    id uuid primary key,
    pedido_id uuid not null references pedido (id),
    transportadora varchar(120) not null,
    guia varchar(120) not null,
    costo_envio numeric(14, 2) not null,
    despachado_en timestamptz not null
);

create index ix_envio_pedido on envio (pedido_id);
