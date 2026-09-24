-- El catalogo pasa de una lista plana por linea a un arbol, y ROPA_Y_CALZADO se parte en dos
-- (24 de septiembre de 2026).
--
-- Hasta hoy `categoria` era una lista plana colgada de la linea, y el negocio no vende asi: una
-- blusa y unas licras no son hermanas de unos tenis, y "Dama" no es una categoria de ropa sino la
-- rama de la que cuelgan nueve. El menu del sitio pinta ahora tres niveles --linea, rama, prenda--
-- y para eso hace falta que una categoria pueda colgar de otra.
--
-- Se hace con una columna y no con una tabla nueva: un arbol de categorias ES una tabla de
-- categorias con padre. Una tabla `subcategoria` habria duplicado el slug, el nombre, la linea y
-- todas sus consultas para no ganar una sola invariante.
alter table categoria
    add column padre_id uuid references categoria (id);

-- La consulta que mas corre sobre esta columna es "dame los hijos de X", que es como el menu y el
-- panel arman el arbol.
create index ix_categoria_padre on categoria (padre_id);

-- ---------------------------------------------------------------------------------------------
-- 1. La linea ROPA_Y_CALZADO se parte en ROPA y CALZADO.
-- ---------------------------------------------------------------------------------------------
--
-- Por que es una linea nueva y no una categoria: son dos surtidos que no comparten talla, ni
-- proveedor, ni criterio de empaque, y sobre todo no comparten lo que se declara en la guia de
-- envio. `ContenidoDeclarado` mapeaba la linea entera a "Ropa y calzado deportivo", asi que la
-- caja de una camiseta sola viajaba declarando calzado. Ahora cada una dice lo suyo.
--
-- Las dos filas que existian con esta linea las creo `SembradorCatalogo` y solo viven en bases de
-- desarrollo; se nombran igual por slug para no depender de eso. El `update` final es la red: si
-- alguna base tuviera otra fila con la linea vieja, cae en ROPA y no en un valor que el enum no
-- sabe leer -- un `LineaCatalogo.valueOf("ROPA_Y_CALZADO")` revienta al listar el catalogo, no al
-- migrar, y ese es el peor sitio donde enterarse.
update categoria set linea = 'CALZADO' where slug = 'calzado-deportivo';
update categoria set linea = 'ROPA' where linea = 'ROPA_Y_CALZADO';

-- ---------------------------------------------------------------------------------------------
-- 2. Las ocho de TECNOLOGIA no se tocan, salvo un nombre.
-- ---------------------------------------------------------------------------------------------
--
-- Conservan id, slug y linea a proposito: son las mismas ocho de `CATEGORIAS_INCLUIDAS` en
-- `.claude/skills/listas-de-proveedor/scripts/parsear_lista.py`, las afirma
-- `CategoriasDeTecnologiaTest`, y sus slugs ya estan en URLs publicadas. Cambiar el slug de
-- `consolas` por decir mejor el nombre habria roto las tres cosas para no ganar nada: el slug no
-- se lee, se navega.
update categoria set nombre = 'Consolas de videojuegos' where slug = 'consolas';

-- ---------------------------------------------------------------------------------------------
-- 3. El primer nivel de ROPA, CALZADO y BOLSOS.
-- ---------------------------------------------------------------------------------------------
--
-- "Dama" y "Caballero" salen en tres lineas distintas y el slug es unico global
-- (`ux_categoria_slug` de V1), asi que llevan la linea delante. No es un adorno: el filtro de la
-- vitrina viaja por slug (`?categoria=ropa-dama`), y dos ramas distintas con el mismo slug serian
-- dos ramas que el filtro no puede distinguir.
--
-- gen_random_uuid() y no UUID v7, por lo mismo que V38: aqui no hay dominio que los genere y el
-- orden temporal de un identificador solo importa donde se pagina por el. Una categoria se lista
-- por nombre.
insert into categoria (id, nombre, slug, linea, padre_id, creado_en) values
    (gen_random_uuid(), 'Dama',      'ropa-dama',         'ROPA',    null, now()),
    (gen_random_uuid(), 'Caballero', 'ropa-caballero',    'ROPA',    null, now()),
    (gen_random_uuid(), 'Dama',      'calzado-dama',      'CALZADO', null, now()),
    (gen_random_uuid(), 'Caballero', 'calzado-caballero', 'CALZADO', null, now()),
    (gen_random_uuid(), 'Unisex',    'calzado-unisex',    'CALZADO', null, now()),
    (gen_random_uuid(), 'Dama',      'bolsos-dama',       'BOLSOS',  null, now())
on conflict (slug) do nothing;

-- ---------------------------------------------------------------------------------------------
-- 4. El segundo nivel, colgado de su padre por slug.
-- ---------------------------------------------------------------------------------------------
--
-- La linea sale del padre (`p.linea`) y no de un literal repetido trece veces: una subcategoria en
-- otra linea que su rama es un nodo que el menu no sabe donde pintar, y la unica forma de
-- garantizar que no pase es no dar la oportunidad de escribirlo mal.
--
-- El slug repite el del padre por la misma razon que arriba: "Busos" y "Sudaderas" existen en Dama
-- y en Caballero. `bolsos-dama-bolsos-de-mano` queda largo y se deja asi: la regla es mecanica
-- --slug del padre mas el nombre-- y una excepcion por estetica obliga a recordar cual fue.
insert into categoria (id, nombre, slug, linea, padre_id, creado_en)
select gen_random_uuid(), h.nombre, h.slug, p.linea, p.id, now()
from (values
    ('ropa-dama',      'Camisas',        'ropa-dama-camisas'),
    ('ropa-dama',      'Blusas',         'ropa-dama-blusas'),
    ('ropa-dama',      'Busos',          'ropa-dama-busos'),
    ('ropa-dama',      'Pantalones',     'ropa-dama-pantalones'),
    ('ropa-dama',      'Faldas',         'ropa-dama-faldas'),
    ('ropa-dama',      'Shorts',         'ropa-dama-shorts'),
    ('ropa-dama',      'Bodis',          'ropa-dama-bodis'),
    ('ropa-dama',      'Licras',         'ropa-dama-licras'),
    ('ropa-dama',      'Sudaderas',      'ropa-dama-sudaderas'),
    ('ropa-caballero', 'Camisetas',      'ropa-caballero-camisetas'),
    ('ropa-caballero', 'Busos',          'ropa-caballero-busos'),
    ('ropa-caballero', 'Sudaderas',      'ropa-caballero-sudaderas'),
    ('ropa-caballero', 'Pantalonetas',   'ropa-caballero-pantalonetas'),
    ('bolsos-dama',    'Bolsos de mano', 'bolsos-dama-bolsos-de-mano'),
    ('bolsos-dama',    'Manos libres',   'bolsos-dama-manos-libres'),
    ('bolsos-dama',    'Morrales',       'bolsos-dama-morrales')
) as h (padre_slug, nombre, slug)
join categoria p on p.slug = h.padre_slug
on conflict (slug) do nothing;

-- ---------------------------------------------------------------------------------------------
-- 5. Se van las tres categorias planas que el arbol reemplaza.
-- ---------------------------------------------------------------------------------------------
--
-- 'ropa-deportiva', 'calzado-deportivo' y 'bolsos' las creo `SembradorCatalogo`, asi que en
-- produccion no existen y esto no borra nada; en una base de desarrollo sembrada si tienen
-- producto detras, y la llave foranea de `producto.categoria_id` haria fallar la migracion.
--
-- Por eso los productos se reapuntan ANTES, y a la hoja que les corresponde de verdad: el
-- sembrador publica una camiseta, unos tenis y un morral. `V62` borro sin reapuntar y estaba bien
-- --sus tres categorias estaban vacias y fallar era lo correcto--; aqui sabemos que no lo estan y
-- adonde va cada uno, asi que fallar solo obligaria a arreglar a mano lo que se puede escribir.
--
-- Si aparece un producto en una de las tres que no sea de la siembra, el `delete` de abajo falla
-- contra la foranea y esa es la respuesta correcta: que alguien mire ese producto.
update producto set categoria_id = (select id from categoria where slug = 'ropa-caballero-camisetas')
where categoria_id in (select id from categoria where slug = 'ropa-deportiva');

update producto set categoria_id = (select id from categoria where slug = 'calzado-unisex')
where categoria_id in (select id from categoria where slug = 'calzado-deportivo');

update producto set categoria_id = (select id from categoria where slug = 'bolsos-dama-morrales')
where categoria_id in (select id from categoria where slug = 'bolsos');

delete from categoria where slug in ('ropa-deportiva', 'calzado-deportivo', 'bolsos');
