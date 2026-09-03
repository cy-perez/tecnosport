-- Numero legible del pedido (TS-2026-000123, apps/api/CLAUDE.md).

-- Contador atomico por anio: "siguiente" es el proximo secuencial a entregar.
-- RepositorioPedidosJpa.siguienteNumero lo reclama con un unico
-- "insert ... on conflict ... returning", sin bloqueo pesimista explicito.
create table secuencia_pedido (
    anio integer primary key,
    siguiente bigint not null default 1
);

alter table pedido add column numero_pedido varchar(20) not null unique;
