-- Los ajustes de ingesta del proveedor (ADR-0089, 25 de octubre de 2026).
--
-- Tenis Al Pie manda tandas de 25 a 35 modelos distintos con un solo precio, sin SKU, sin tallas en
-- el anuncio y sin existencias. Cuatro cosas de su chat no se parecen a las de los demas, y las
-- cuatro se guardan en el proveedor:
--
-- - una_foto_un_producto: cada foto de la publicacion es un producto.
-- - venta_bajo_confirmacion: la talla se confirma por WhatsApp antes de cobrar (ADR-0086).
-- - dias_de_vigencia: cuantos dias sigue a la venta lo que no vuelve a aparecer; nulo es la
--   ventana general (ADR-0066).
-- - tallas_dama, tallas_caballero y ajuste_de_talla: las tallas que maneja siempre, como las
--   nombra el, separadas por coma, y cuantas tallas mas arriba las nombra (ADR-0085). Nulas si las
--   dice cada anuncio. Las tres van juntas: con una sola lista la otra queda vacia, no nula.
alter table proveedor add column una_foto_un_producto boolean not null default false;
alter table proveedor add column venta_bajo_confirmacion boolean not null default false;
alter table proveedor add column dias_de_vigencia smallint;
alter table proveedor add column tallas_dama varchar(200);
alter table proveedor add column tallas_caballero varchar(200);
alter table proveedor add column ajuste_de_talla smallint;

alter table proveedor add constraint proveedor_dias_de_vigencia_rango
  check (dias_de_vigencia is null or dias_de_vigencia between 1 and 30);
alter table proveedor add constraint proveedor_tallas_por_genero_juntas
  check ((tallas_dama is null) = (tallas_caballero is null)
     and (tallas_dama is null) = (ajuste_de_talla is null));
