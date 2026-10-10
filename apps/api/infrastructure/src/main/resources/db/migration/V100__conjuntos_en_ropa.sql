-- Conjuntos entra en las dos ramas de ropa (10 de octubre de 2026).
--
-- El negocio surte conjuntos y el arbol no los tenia. No es un descuido de `V63`: es que el dato
-- venia apareciendo por los bordes sin que nadie cerrara el circulo. `V91` subio la escala de
-- tallas agrupadas --S-M, M-L, L-XL-- a `ropa-dama` y `ropa-caballero` diciendo en su propio
-- comentario que "los conjuntos deportivos y varias prendas de dama no tallan S o M sino S-M", y
-- `NombreDeCategoria` ya conoce "conjunto/conjuntos/conjuntico" para leer las listas de proveedor.
-- O sea que el sistema sabia tallar un conjunto y sabia nombrarlo, pero no habia donde colgarlo:
-- un producto no puede publicarse sin categoria.
--
-- Lo levanto una barrida de fixtures: varias pruebas montaban `Categoria.crear("Conjuntos", new
-- Slug("conjuntos"), ROPA)`, una categoria que no existia en ninguna base. La salida no era
-- renombrar la prueba sino arreglar el catalogo, porque la prueba estaba describiendo el negocio
-- mejor que la migracion.
--
-- Es una fila y no codigo, que es la regla de `ADR-0061`: una linea nueva es codigo; una categoria
-- nueva es una fila. Va como migracion y no creada a mano desde el panel para que desarrollo,
-- pruebas y produccion arranquen con el mismo arbol, igual que `V63` y `V64`.
--
-- Dos hojas y no una: un conjunto de dama no es una subcategoria de la rama de caballero ni al
-- contrario, y el slug lleva la rama delante porque es unico global (`ux_categoria_slug` de V1) y
-- el filtro de la vitrina viaja por slug (`?categoria=ropa-dama-conjuntos`).
--
-- Sin `escala_tallas` propia, y es a proposito: una hoja con la escala vacia hereda la de su rama
-- (`Categoria.escalaEfectiva`), que desde `V91` es exactamente la agrupada que un conjunto
-- necesita. Ponerla aqui seria copiar trece tallas que ya estan arriba y que divergirian la
-- proxima vez que alguien toque una sola de las dos copias.
insert into categoria (id, nombre, slug, linea, padre_id, creado_en)
select gen_random_uuid(), 'Conjuntos', h.slug, p.linea, p.id, now()
from (values
    ('ropa-dama',      'ropa-dama-conjuntos'),
    ('ropa-caballero', 'ropa-caballero-conjuntos')
) as h (padre_slug, slug)
join categoria p on p.slug = h.padre_slug
on conflict (slug) do nothing;
