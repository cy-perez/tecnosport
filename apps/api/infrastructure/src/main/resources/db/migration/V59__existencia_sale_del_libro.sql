-- La existencia sale del libro de movimientos, y la columna del catalogo desaparece
-- (20 de septiembre de 2026, adr/0050).
--
-- `variante.existencia` venia de la Fase 1, cuando el catalogo era de solo lectura y el agregado
-- Inventario no existia todavia. Cuando llego, la columna se quedo: la movia el alta de la variante
-- y el ajuste por conteo, mientras el libro bajaba con cada venta. La vitrina leia la columna, asi
-- que vender las cinco unidades de algo no cambiaba el numero que veia quien compraba. adr/0049
-- eligio la salida corta —el ajuste escribia en los dos sitios— y dejo esta escrita como la
-- correcta.
--
-- ANTES DE BORRAR: las variantes que no tienen libro se quedarian sin ninguna existencia, porque la
-- columna seria el unico sitio donde estaba el dato. Puede haberlas: hasta hoy el sembrador escribia
-- entidades JPA directas y el libro lo abria un segundo sembrador, y cualquier carga por SQL a mano
-- se salta las dos. A esas se les abre el libro con lo que la columna decia.
--
-- Lo que NO se hace, a proposito: cuadrar hacia arriba las variantes cuyo libro ya existe y dice
-- menos que la columna. Esa diferencia no es un dato perdido, es justamente la venta que el libro
-- registro y la columna no vio. El libro es la verdad; la columna, la copia que envejecio.
insert into inventario (id, variante_id)
select gen_random_uuid(), v.id
from variante v
where not exists (select 1 from inventario i where i.variante_id = v.id);

insert into movimiento_inventario (id, inventario_id, tipo, cantidad, creado_en, motivo)
select gen_random_uuid(),
       i.id,
       'ENTRADA',
       v.existencia,
       now(),
       'Apertura del libro al borrar la columna del catalogo (adr/0050)'
from variante v
         join inventario i on i.variante_id = v.id
where v.existencia > 0
  and not exists (select 1 from movimiento_inventario m where m.inventario_id = i.id);

alter table variante
    drop column existencia;
