-- La bitacora de las tareas programadas (ADR-0104, 3 de noviembre de 2026).
--
-- Hasta aqui cada tarea era un @Scheduled y guardaba en memoria cuando corrio por ultima vez: un
-- reinicio borraba la cuenta. Ahora las dispara un tick de Cloud Scheduler y la API puede apagarse
-- entre uno y otro, asi que la cuenta vive en la base.
--
-- Una fila por tarea, creada la primera vez que el tick la ve. No hay historial: solo la ultima vuelta.
--   tomada_en       cuando empezo la ultima vuelta; de aqui se cuenta el intervalo. La primera vez
--                   que el tick ve la tarea la anota sin correrla, como el retraso inicial de antes.
--   terminada_en    cuando termino la ultima vez, bien o mal. Nula si nunca termino.
--   en_curso_hasta  el arriendo de quien la tiene. Nula si nadie; vencida, es de quien la tome.
--   arrendada_por   la marca de quien la tiene: solo esa vuelta puede soltarla.
--   fallo           el motivo si la ultima vuelta fallo. Nula si salio bien.
create table ejecucion_de_tarea (
  nombre         varchar(100) primary key,
  tomada_en      timestamptz not null,
  terminada_en   timestamptz,
  en_curso_hasta timestamptz,
  arrendada_por  uuid,
  fallo          text
);
