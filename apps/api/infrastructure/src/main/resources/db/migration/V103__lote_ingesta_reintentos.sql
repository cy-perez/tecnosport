-- Cuantas veces volvio a la cola un lote cuyo trabajador murio (ADR-0080, 11 de octubre de 2026).
--
-- Desde que la ingesta corre en tramos por Cloud Tasks, un lote abandonado no se cierra con error:
-- vuelve a la cola y el tramo siguiente lo retoma donde iba. A la cuarta vez se da por fallido,
-- porque un lote que tumba siempre a su trabajador lo seguiria tumbando.
alter table lote_ingesta add column reintentos integer not null default 0;

alter table lote_ingesta add constraint ck_lote_ingesta_reintentos check (reintentos >= 0);
