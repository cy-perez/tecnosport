-- El precio de contado es mayor que cero (27 de octubre de 2026, revision adversarial).
--
-- La V123 dejo `precio >= precio_contado` y nada mas, asi que una variante con contado 0 pasaba:
-- con pasarela se cobraba el publicado, pero con transferencia, en el punto o contra entrega se
-- vendia regalada. El dominio ya lo rechazaba al crear y al cambiar el precio; desde hoy tambien al
-- reconstruir, y esto lo exige a cualquier otra puerta. Con contado > 0, el publicado tambien lo es.
--
-- Si ya hay variantes en cero, la migracion FALLA en vez de corregirlas: el precio que deberian
-- tener es un dato del negocio, y esto no lo puede inventar. Se fijan desde el panel (o con un
-- update a mano) y se vuelve a desplegar.
do $$
declare
    en_cero text;
begin
    select string_agg(sku, ', ' order by sku) into en_cero
    from variante
    where precio_contado <= 0;
    if en_cero is not null then
        raise exception 'V135: hay variantes con el precio de contado en cero o menos (%). Fija su precio desde el panel y vuelve a desplegar: la migracion no inventa un precio.', en_cero;
    end if;
end
$$;

alter table variante add constraint ck_variante_precio_contado_positivo
    check (precio_contado > 0);
