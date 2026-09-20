-- El identificador que la pasarela le da a su propia transacción dejó de ser solo de Wompi
-- (adr/0048): Sistecrédito devuelve el suyo en `_id` y cumple exactamente el mismo papel —es lo
-- que hay que enviarle para consultar el estado, y sin él la conciliación no tiene por dónde
-- preguntar.
--
-- Se renombra en vez de agregar una segunda columna: es el mismo hecho. Un pago pertenece a una
-- sola pasarela, la suya, y `pago.metodo_pago` ya dice a cuál.
alter table pago rename column id_transaccion_wompi to id_transaccion_pasarela;

-- El índice parcial `ix_pago_conciliacion` de V8 filtra por esta columna en su predicado. No hay
-- que recrearlo: PostgreSQL guarda el predicado referenciando la columna por su número interno,
-- no por su nombre, así que el renombrado lo arrastra. El nombre del índice tampoco se toca — no
-- menciona la pasarela.
