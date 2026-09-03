-- Conciliación programada de Wompi (Fase 3). Ver docs/11-pagos-y-envios.md.

-- Se registra al volver el cliente del Web Checkout, con el id que trae la
-- URL de retorno. Sin él la conciliación no tiene cómo consultar la API de
-- Wompi, que busca por su id, no por la referencia propia.
alter table pago add column id_transaccion_wompi varchar(120);

-- La conciliación programada filtra exactamente por estos tres campos
-- (docs/11-pagos-y-envios.md): PENDIENTE, con id de Wompi, más viejos que un
-- umbral. Índice parcial: sin él sería un recorrido completo de pago a
-- medida que crezca la tabla.
create index ix_pago_conciliacion on pago (creado_en)
    where estado = 'PENDIENTE' and id_transaccion_wompi is not null;
