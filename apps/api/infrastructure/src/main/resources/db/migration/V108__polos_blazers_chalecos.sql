-- Polos en la rama de caballero, y Blazers y Chalecos en la de dama (11 de octubre de 2026).
--
-- Las tres salen de la ingesta de La Riverah y de Violeta. La Riverah vende camisas tipo polo --con
-- cuello--, y quedaban como camisetas con el titulo "Camiseta estilo Polo importada", que en una
-- replica quiere decir la marca Polo y no la prenda. Violeta vende blazers y chalecos, y el
-- extractor los dejaba en OTRO "porque el catalogo no tiene donde ponerlos": ahora lo tiene.
--
-- `ropa-caballero-polos` ya aparecia en V89, en la lista de pesos, porque en la nube se habia creado
-- a mano; en las migraciones no existia, y desarrollo y pruebas arrancaban sin ella. Va con `on
-- conflict do nothing`: donde ya existe, no se toca. Como en V100, una categoria nueva es una fila
-- y no codigo (ADR-0061), y sin `escala_tallas` propia: hereda la de su rama.
insert into categoria (id, nombre, slug, linea, padre_id, creado_en)
select gen_random_uuid(), h.nombre, h.slug, p.linea, p.id, now()
from (values
    ('ropa-caballero', 'Polos',    'ropa-caballero-polos'),
    ('ropa-dama',      'Blazers',  'ropa-dama-blazers'),
    ('ropa-dama',      'Chalecos', 'ropa-dama-chalecos')
) as h (padre_slug, nombre, slug)
join categoria p on p.slug = h.padre_slug
on conflict (slug) do nothing;

-- Los pesos de envio los dio el negocio el 11 de octubre de 2026: 300 g el polo (el mismo de V89),
-- 600 g el blazer y 400 g el chaleco. Donde ya hay uno puesto desde el panel, se respeta.
insert into envio_peso_referencia (categoria_id, peso_gramos)
select c.id, p.peso_gramos
from categoria c
join (values
    ('ropa-caballero-polos', 300),
    ('ropa-dama-blazers',    600),
    ('ropa-dama-chalecos',   400)
) as p (slug, peso_gramos) on p.slug = c.slug
on conflict (categoria_id) do nothing;
