-- Las publicaciones fallidas de un lote se cuentan aparte de las descartadas (11 de octubre de 2026).
--
-- Hasta hoy ProcesarLoteDeIngesta sumaba a resumen_descartes las publicaciones que quedaban en
-- ERROR: la ingesta de Meraki salio con "23 descartadas" cuando la API del modelo habia rechazado
-- las 23 sin leer una. Descartada quiere decir "se leyo y no era un producto"; fallida, "no se
-- pudo leer". El panel tiene que poder distinguirlas, y reintentar solo las segundas.
alter table lote_ingesta add column resumen_fallidas integer;

-- Los lotes de antes: las fallidas salen de contar sus publicaciones en ERROR, y se restan de los
-- descartes, que es donde estaban contadas. Solo en los lotes con resumen: el que no lo tiene
-- todavia no conto nada.
update lote_ingesta l
   set resumen_fallidas = coalesce(f.cuantas, 0),
       resumen_descartes = greatest(l.resumen_descartes - coalesce(f.cuantas, 0), 0)
  from (select l2.id, (select count(*) from publicacion_proveedor p
                        where p.lote_id = l2.id and p.estado = 'ERROR') as cuantas
          from lote_ingesta l2) f
 where f.id = l.id
   and l.resumen_mensajes_leidos is not null;
