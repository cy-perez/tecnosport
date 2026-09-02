-- Inventario (Fase 2). Ver docs/02-modelo-datos.md.

-- Inventario es un agregado propio, con su propia identidad, aunque hoy sea
-- 1:1 con variante: el saldo nunca se edita, se agrega movimiento.
create table inventario (
    id uuid primary key,
    variante_id uuid not null references variante (id),
    constraint ux_inventario_variante unique (variante_id)
);

create table movimiento_inventario (
    id uuid primary key,
    inventario_id uuid not null references inventario (id),
    tipo varchar(20) not null,
    cantidad integer not null,
    creado_en timestamptz not null default now(),
    -- Solo RESERVA la usa; null = no vence (contraentrega).
    expira_en timestamptz,
    -- SALIDA/LIBERACION que resuelven una RESERVA apuntan a su id.
    referencia_id uuid,
    motivo text
);

create index ix_movimiento_inventario_inventario on movimiento_inventario (inventario_id);
