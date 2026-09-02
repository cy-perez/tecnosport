-- Carrito (Fase 2). Ver docs/02-modelo-datos.md.

create table carrito (
    id uuid primary key,
    usuario_id uuid,
    creado_en timestamptz not null default now()
);

-- Sin FK a variante a propósito: agregar al carrito no valida que la
-- variante exista (docs/00-producto.md, la reserva ocurre al iniciar el
-- pago, no antes). Una FK convertiría esa decisión de negocio en un error
-- crudo de Postgres.
create table linea_carrito (
    id uuid primary key,
    carrito_id uuid not null references carrito (id) on delete cascade,
    variante_id uuid not null,
    cantidad integer not null
);
