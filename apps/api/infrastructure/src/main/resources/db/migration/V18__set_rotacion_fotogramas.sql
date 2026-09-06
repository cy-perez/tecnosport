-- Cuántos fotogramas se prometieron al abrir el set (docs/02-modelo-datos.md ya listaba esta
-- columna; la tabla de V1 nunca la tuvo). El asistente de captura promete N al abrir el set y
-- `completar` verifica que llegaron los N: sin la promesa, un set de 8 que termina con 4
-- fotogramas contiguos —cuatro subidas perdidas— pasaría como un set de 4 perfectamente válido.
alter table set_rotacion add column fotogramas integer not null default 0;

-- Los sets que ya existen están completos por construcción, así que lo prometido es lo que tienen.
update set_rotacion s
set fotogramas = (select count(*) from imagen_producto i where i.set_rotacion_id = s.id);

-- El valor por defecto era solo para poder agregar la columna sobre las filas existentes: abrir un
-- set sin decir cuántos fotogramas va a tener no es un caso válido.
alter table set_rotacion alter column fotogramas drop default;
