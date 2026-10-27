-- ADR-0097, "Retiro del sistema 360". El negocio dejó de usar el visor 360 de la ficha y el
-- asistente de captura del panel, así que el agregado SetRotacion salió del código y con él la
-- tabla que lo guardaba. Ningún adaptador lee ni escribe ya `set_rotacion` ni la columna
-- `imagen_producto.set_rotacion_id`, y `TipoImagen` ya no tiene ROTACION: una fila de ese tipo
-- que se quedara haría reventar al hidratador (`TipoImagen.valueOf`) en la ficha y en el catálogo.
--
-- Los objetos del bucket (`productos/{id}/rotacion/`) no se tocan desde aquí: una migración no
-- habla con Cloud Storage. Quedan como huérfanos y los informa `npm run huerfanos`.

-- Primero los fotogramas: sus variantes en `imagen_variante` caen con ellos por el
-- `on delete cascade` de la V60.
delete from imagen_producto where tipo = 'ROTACION';

-- Después la columna que apuntaba al set, con su índice, y al final la tabla: mientras la
-- columna exista, la foránea impide soltar `set_rotacion`.
drop index if exists ix_imagen_producto_set_rotacion;
alter table imagen_producto drop column set_rotacion_id;
drop table set_rotacion;
