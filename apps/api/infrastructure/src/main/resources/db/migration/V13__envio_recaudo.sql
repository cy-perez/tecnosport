-- Recaudo de contraentrega (docs/11-pagos-y-envios.md): la comisión de la transportadora es un
-- costo real, separado del flete (costo_envio, ya existente), para que el margen del pedido sea
-- verdadero. Ambas columnas nulas hasta que se concilia.
alter table envio add column comision_recaudo numeric(14, 2);
alter table envio add column recaudo_conciliado_en timestamptz;
