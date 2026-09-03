-- Pago (Fase 3, Wompi). Ver docs/02-modelo-datos.md y docs/11-pagos-y-envios.md.

create table pago (
    id uuid primary key,
    pedido_id uuid not null references pedido (id),
    referencia varchar(80) not null unique,
    metodo_pago varchar(30) not null,
    monto numeric(14, 2) not null,
    estado varchar(20) not null,
    creado_en timestamptz not null,
    actualizado_en timestamptz not null
);

-- Un pedido puede tener varios intentos de pago (reintento tras PAGO_FALLIDO).
create index ix_pago_pedido on pago (pedido_id);

-- Un evento por notificación de webhook recibida. id_evento es lo que hace
-- idempotente un reintento del webhook (docs/03-api.md); único por pago, no
-- global, porque la idempotencia se decide dentro del agregado Pago.
create table evento_pago (
    id uuid primary key,
    pago_id uuid not null references pago (id) on delete cascade,
    id_evento varchar(120) not null,
    estado varchar(20) not null,
    recibido_en timestamptz not null,
    unique (pago_id, id_evento)
);

create index ix_evento_pago_pago on evento_pago (pago_id);
