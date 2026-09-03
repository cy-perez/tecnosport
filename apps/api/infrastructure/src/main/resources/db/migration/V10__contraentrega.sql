-- Disponibilidad de contraentrega (Fase 3). Ver docs/11-pagos-y-envios.md.

-- "Tabla propia, alimentada por lo que cubre la transportadora con recaudo":
-- sin integración automática todavía, la carga el administrador a mano.
-- El código DANE de la ciudad ya es único por naturaleza: sirve de clave
-- primaria sin necesitar un id aparte.
create table cobertura_contraentrega (
    codigo_dane_ciudad varchar(10) primary key
);

-- MetodosDePagoDisponibles consulta si un correo ya rechazó un pedido en la
-- entrega (docs/11-pagos-y-envios.md); sin este índice esa consulta barre
-- toda la tabla en cada intento de pedido contraentrega.
create index ix_pedido_correo on pedido (correo);
