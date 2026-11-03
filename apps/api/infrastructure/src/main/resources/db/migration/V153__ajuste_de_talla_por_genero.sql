-- El ajuste de talla del proveedor, uno por genero (ADR-0103, 3 de noviembre de 2026).
--
-- V118 guardo un solo ajuste para las tallas de dama y las de caballero, porque ADR-0085 decia que
-- la talla EUR es la nacional mas una. En caballero no lo es: el EUR 42 de caballero de Tenis Al Pie
-- mide 26,5 cm, que es la 39 colombiana, y la guia del proveedor dice lo mismo para toda su escala
-- de caballero (EUR 40 a 44 son la 37 a la 41). En dama si es mas una.
--
-- El ajuste que habia pasa a los dos generos: nada cambia hasta que alguien lo corrija en la ficha
-- del proveedor. El 3 de caballero de Tenis Al Pie es un dato del negocio y se pone en el panel, no
-- aqui.
alter table proveedor rename column ajuste_de_talla to ajuste_dama;
alter table proveedor add column ajuste_caballero smallint;
update proveedor set ajuste_caballero = ajuste_dama where ajuste_dama is not null;

alter table proveedor drop constraint proveedor_tallas_por_genero_juntas;
alter table proveedor add constraint proveedor_tallas_por_genero_juntas
  check ((tallas_dama is null) = (tallas_caballero is null)
     and (tallas_dama is null) = (ajuste_dama is null)
     and (tallas_dama is null) = (ajuste_caballero is null));
