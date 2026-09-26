-- Bolsos abre la rama de Caballero, con Morrales dentro (26 de septiembre de 2026).
--
-- `V63` dejo BOLSOS con una sola rama, Dama, y tres hojas: bolsos de mano, manos libres y
-- morrales. Era el surtido que habia. El negocio pidio hoy el morral de caballero, y eso no es una
-- hoja nueva colgada de Dama --un morral de hombre no es una subcategoria de la rama de mujer--
-- sino la segunda rama de la linea, que la deja con la misma forma que ROPA y CALZADO ya tienen.
--
-- Es una fila y no codigo, que es la regla que `ADR-0061` dejo escrita: una linea nueva es codigo;
-- una categoria nueva es una fila. Va como migracion y no creada a mano desde el panel para que
-- desarrollo, pruebas y produccion arranquen con el mismo arbol -- que es lo mismo que hizo `V63`
-- con las dieciseis anteriores.
--
-- `on conflict (slug) do nothing` en las dos: el slug es unico global (`ux_categoria_slug` de V1) y
-- estas dos filas pueden existir ya si alguien las creo desde el panel antes de desplegar esto.

-- La rama. El slug lleva la linea delante por lo mismo que en `V63`: "Caballero" existe tambien en
-- ROPA y en CALZADO, el filtro de la vitrina viaja por slug (`?categoria=bolsos-caballero`) y dos
-- ramas con el mismo slug son dos ramas que el filtro no puede distinguir.
insert into categoria (id, nombre, slug, linea, padre_id, creado_en)
values (gen_random_uuid(), 'Caballero', 'bolsos-caballero', 'BOLSOS', null, now())
on conflict (slug) do nothing;

-- La hoja. La linea sale del padre y no de un literal repetido, igual que en `V63`: una
-- subcategoria en otra linea que su rama es un nodo que el menu no sabe donde pintar.
insert into categoria (id, nombre, slug, linea, padre_id, creado_en)
select gen_random_uuid(), 'Morrales', 'bolsos-caballero-morrales', p.linea, p.id, now()
from categoria p
where p.slug = 'bolsos-caballero'
on conflict (slug) do nothing;
