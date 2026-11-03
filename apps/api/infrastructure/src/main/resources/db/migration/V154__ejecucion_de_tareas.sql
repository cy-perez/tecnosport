-- La bitacora de las tareas programadas (ADR-0104, 3 de noviembre de 2026).
--
-- Hasta aqui cada tarea era un @Scheduled y guardaba en memoria cuando corrio por ultima vez: un
-- reinicio borraba la cuenta. Ahora las dispara un tick de Cloud Scheduler y la API puede apagarse
-- entre uno y otro, asi que la cuenta vive en la base.
--
-- Una fila por tarea, creada la primera vez que se toma. No hay historial: solo la ultima vuelta.
--   terminada_en    cuando termino la ultima vez, bien o mal. Nula si nunca termino.
--   en_curso_hasta  el arriendo de quien la tiene. Nula si nadie; vencida, es de quien la tome.
--   fallo           el motivo si la ultima vuelta fallo. Nula si salio bien.
create table ejecucion_de_tarea (
  nombre         varchar(100) primary key,
  terminada_en   timestamptz,
  en_curso_hasta timestamptz,
  fallo          text
);
