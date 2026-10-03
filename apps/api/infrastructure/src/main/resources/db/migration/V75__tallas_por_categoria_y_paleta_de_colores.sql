-- Tallas por categoria, talla unica con "sirve hasta" y paleta de colores (3 de octubre de 2026).

-- ---------------------------------------------------------------------------------------------
-- 1. La escala de tallas de una categoria.
-- ---------------------------------------------------------------------------------------------
--
-- Las tallas en su orden, una por linea: la ficha las ensena todas, con las que el producto no
-- trae o tiene agotadas tachadas, y la revision de un borrador las ofrece para marcar. Una hoja
-- sin escala usa la de su rama: Camisas, Blusas y Bodis heredan la de Ropa > Dama, y Jeans la
-- reemplaza con la suya. Texto y no tabla por lo mismo que las listas del borrador (V71): se lee
-- entera con la categoria y nunca se filtra por una talla.
alter table categoria add column escala_tallas text;

-- ---------------------------------------------------------------------------------------------
-- 2. Hasta que talla le sirve una prenda de talla unica.
-- ---------------------------------------------------------------------------------------------
--
-- Solo si el proveedor lo dijo. La talla unica en si es una variante con Talla = "Unica"; esto es
-- lo que la tarjeta y la ficha dicen al lado.
alter table producto add column talla_sirve_hasta varchar(20);

-- ---------------------------------------------------------------------------------------------
-- 3. Las hojas que faltaban: Jeans y Calzado deportivo.
-- ---------------------------------------------------------------------------------------------
--
-- Mismo patron que V63: el slug repite el del padre y la linea sale del padre.
insert into categoria (id, nombre, slug, linea, padre_id, creado_en)
select gen_random_uuid(), h.nombre, h.slug, p.linea, p.id, now()
from (values
    ('ropa-dama',         'Jeans',     'ropa-dama-jeans'),
    ('ropa-caballero',    'Jeans',     'ropa-caballero-jeans'),
    ('calzado-dama',      'Deportivo', 'calzado-dama-deportivo'),
    ('calzado-caballero', 'Deportivo', 'calzado-caballero-deportivo'),
    ('calzado-unisex',    'Deportivo', 'calzado-unisex-deportivo')
) as h (padre_slug, nombre, slug)
join categoria p on p.slug = h.padre_slug
on conflict (slug) do nothing;

-- Las ramas de calzado eran hojas: un producto podia colgar de ellas, y en una base sembrada los
-- tenis de `SembradorCatalogo` cuelgan de calzado-unisex (V63). Con una hija ya no son hojas, asi
-- que lo que tenian pasa a su hoja Deportivo, que es lo que el negocio vende en calzado.
update producto pr set categoria_id = hoja.id
from categoria rama
join categoria hoja on hoja.padre_id = rama.id and hoja.slug = rama.slug || '-deportivo'
where pr.categoria_id = rama.id
  and rama.slug in ('calzado-dama', 'calzado-caballero', 'calzado-unisex');

-- ---------------------------------------------------------------------------------------------
-- 4. Las escalas que dio el negocio.
-- ---------------------------------------------------------------------------------------------
--
-- Ropa en las ramas, para que las hojas la hereden; Jeans y Calzado en las suyas. E'\n' separa.
update categoria set escala_tallas = E'XS\nS\nM\nL\nXL\nXXL\nXXXL'
where slug in ('ropa-dama', 'ropa-caballero');

update categoria set escala_tallas = E'26\n28\n30\n32\n34\n36\n38\n40\n42'
where slug in ('ropa-dama-jeans', 'ropa-caballero-jeans');

update categoria set escala_tallas = E'34\n35\n36\n37\n38\n39\n40\n41\n42\n43'
where slug in ('calzado-dama', 'calzado-caballero', 'calzado-unisex');

-- ---------------------------------------------------------------------------------------------
-- 5. La paleta de colores.
-- ---------------------------------------------------------------------------------------------
--
-- Los colores que se le asignan a cada foto al revisar un borrador, con el HEX que pinta la
-- muestra en la tarjeta y en la ficha. Viven aqui y no en el frontend porque son un dato del
-- producto, no del sistema visual: la regla de "ningun HEX en el frontend" sigue intacta. El nombre
-- en espanol es el valor del atributo Color; el ingles, el que se le dice a quien compra en ingles.
create table color_paleta (
    id uuid primary key,
    nombre varchar(40) not null,
    nombre_en varchar(40) not null,
    hex varchar(7) not null,
    orden integer not null,
    constraint ck_color_paleta_hex check (hex ~ '^#[0-9A-F]{6}$')
);

create unique index ux_color_paleta_nombre on color_paleta (lower(nombre));

insert into color_paleta (id, nombre, nombre_en, hex, orden) values
    (gen_random_uuid(), 'Negro',         'Black',       '#111111',  1),
    (gen_random_uuid(), 'Blanco',        'White',       '#FFFFFF',  2),
    (gen_random_uuid(), 'Gris',          'Grey',        '#8C8C8C',  3),
    (gen_random_uuid(), 'Beige',         'Beige',       '#D8C3A5',  4),
    (gen_random_uuid(), 'Camel',         'Camel',       '#C19A6B',  5),
    (gen_random_uuid(), 'Café',          'Brown',       '#6F4E37',  6),
    (gen_random_uuid(), 'Vino',          'Burgundy',    '#722F37',  7),
    (gen_random_uuid(), 'Rojo',          'Red',         '#C62828',  8),
    (gen_random_uuid(), 'Coral',         'Coral',       '#FF7F50',  9),
    (gen_random_uuid(), 'Naranja',       'Orange',      '#EF6C00', 10),
    (gen_random_uuid(), 'Mostaza',       'Mustard',     '#D4A017', 11),
    (gen_random_uuid(), 'Amarillo',      'Yellow',      '#FBC02D', 12),
    (gen_random_uuid(), 'Verde menta',   'Mint green',  '#98D8C8', 13),
    (gen_random_uuid(), 'Verde',         'Green',       '#2E7D32', 14),
    (gen_random_uuid(), 'Verde militar', 'Olive green', '#4B5320', 15),
    (gen_random_uuid(), 'Azul cielo',    'Sky blue',    '#87CEEB', 16),
    (gen_random_uuid(), 'Azul',          'Blue',        '#1565C0', 17),
    (gen_random_uuid(), 'Azul marino',   'Navy blue',   '#1B2A4A', 18),
    (gen_random_uuid(), 'Lila',          'Lilac',       '#C8A2C8', 19),
    (gen_random_uuid(), 'Morado',        'Purple',      '#6A1B9A', 20),
    (gen_random_uuid(), 'Rosado',        'Pink',        '#F4A6C1', 21),
    (gen_random_uuid(), 'Fucsia',        'Fuchsia',     '#D81B60', 22),
    (gen_random_uuid(), 'Dorado',        'Gold',        '#C9A227', 23),
    (gen_random_uuid(), 'Plateado',      'Silver',      '#C0C0C0', 24);
