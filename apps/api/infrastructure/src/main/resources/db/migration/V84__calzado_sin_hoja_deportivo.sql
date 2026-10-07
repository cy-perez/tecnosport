-- Calzado deportivo vuelve a tres hojas: Dama, Caballero y Unisex (6 de octubre de 2026).
--
-- `V75` §3 le colgo a cada rama de calzado una hoja llamada "Deportivo", por simetria con Jeans en
-- ropa. En ropa la hoja distingue -- Dama tiene Jeans, Blusas, Licras y nueve mas -- y en calzado
-- no distinguia nada: la rama tenia una sola hija y el negocio no vende otra cosa que calzado
-- deportivo, asi que el arbol pedia tres clics para llegar a lo unico que hay y el desplegable del
-- filtro decia "Calzado deportivo > Dama > Deportivo", con la palabra repetida.
--
-- Lo que se deshace es solo eso. Jeans se queda: ahi la hoja si separa.

-- ---------------------------------------------------------------------------------------------
-- 1. Las etiquetas de la hoja suben a la rama.
-- ---------------------------------------------------------------------------------------------
--
-- Antes de borrar nada, porque `categoria_hashtag` va con `on delete cascade` y un borrado se las
-- llevaria en silencio. Solo si la rama no tiene ninguna: si alguien ya le puso las suyas, mandan
-- las de la rama -- es la categoria que sobrevive y la que se va a difundir.
insert into categoria_hashtag (categoria_id, orden, valor)
select rama.id, h.orden, h.valor
from categoria hoja
join categoria rama on rama.id = hoja.padre_id
join categoria_hashtag h on h.categoria_id = hoja.id
where hoja.slug in ('calzado-dama-deportivo', 'calzado-caballero-deportivo', 'calzado-unisex-deportivo')
  and not exists (select 1 from categoria_hashtag r where r.categoria_id = rama.id)
on conflict (categoria_id, orden) do nothing;

-- ---------------------------------------------------------------------------------------------
-- 2. Los productos vuelven a la rama.
-- ---------------------------------------------------------------------------------------------
--
-- El camino inverso al de `V75` §3. `producto.categoria_id` es `not null references categoria`, asi
-- que esto tiene que ir antes del borrado o la llave foranea hace fallar la migracion -- el mismo
-- orden que `V63` ya tuvo que respetar.
update producto pr set categoria_id = rama.id
from categoria hoja
join categoria rama on rama.id = hoja.padre_id
where pr.categoria_id = hoja.id
  and hoja.slug in ('calzado-dama-deportivo', 'calzado-caballero-deportivo', 'calzado-unisex-deportivo');

-- ---------------------------------------------------------------------------------------------
-- 3. Y las hojas se van.
-- ---------------------------------------------------------------------------------------------
--
-- Sin tocar la escala de tallas: `V75` §4 la puso en las ramas (34 a 43) y las hojas nacieron sin
-- ninguna, heredandola. Al desaparecer la hoja, la rama sigue diciendo lo mismo que decia.
delete from categoria
where slug in ('calzado-dama-deportivo', 'calzado-caballero-deportivo', 'calzado-unisex-deportivo');
