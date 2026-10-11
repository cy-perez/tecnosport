-- El latido del trabajador que tiene el lote (11 de octubre de 2026).
--
-- Se renueva cada pocos segundos mientras el lote se procesa, esta en pausa o se esta deteniendo.
-- Con el, la instancia que arranca distingue el lote que una vecina viva esta leyendo del que quedo
-- abierto porque su instancia murio: antes los dos eran PROCESANDO y cerraba los dos. Nula en los
-- lotes que nunca se tomaron y en los de antes; esos cuentan desde iniciado_en.
alter table lote_ingesta add column latido_en timestamptz;
