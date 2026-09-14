-- La linea CELULARES pasa a llamarse TECNOLOGIA, y entran las categorias que faltaban
-- (14 de septiembre de 2026).
--
-- El negocio amplio lo tecnologico mas alla del celular: relojes, audifonos, cargadores, cables de
-- cargador, power banks, consolas, parlantes, computadores, tablets y proyectores. Todos son
-- CATEGORIAS dentro de esta linea, no lineas propias -- el nivel grueso sigue siendo de tres, y
-- afinar es lo que hace la tabla categoria.
--
-- "Celulares" sigue existiendo, pero como categoria: la fila que antes tenia linea='CELULARES' y
-- nombre='Celulares' conserva su nombre, su slug y su id, y solo cambia de linea. Ningun producto
-- ni ningun pedido se toca: la linea vive en la categoria, no en el producto.
update categoria set linea = 'TECNOLOGIA' where linea = 'CELULARES';

-- Las categorias van AQUI y no en SembradorCatalogo, y la diferencia importa: el sembrador solo
-- corre cuando la tabla de productos esta vacia (`if (productos.count() > 0) return;`), asi que
-- todo lo que ponga ahi no llega nunca a una base que ya tiene datos -- ni a la de desarrollo de
-- quien ya sembro, ni a produccion. Se comprobo poniendolas ahi primero: la migracion renombro la
-- linea y las diez categorias no aparecieron por ningun lado.
--
-- Y hay una razon de fondo, no solo mecanica: el sembrador es ficcion declarada -- "Under Trail" y
-- el "Celular TecnoSport Aurora" no existen-- mientras que estas categorias son dato real del
-- negocio. El dato real que toda instalacion necesita es una migracion; el ejemplo para poder
-- desarrollar es una siembra.
--
-- gen_random_uuid() y no UUID v7 como el resto del sistema: aqui no hay dominio que los genere, y
-- el orden temporal de un identificador solo importa donde se pagina por el. Una categoria se
-- lista por nombre.
insert into categoria (id, nombre, slug, linea, creado_en) values
    (gen_random_uuid(), 'Relojes',            'relojes',            'TECNOLOGIA', now()),
    (gen_random_uuid(), 'Audífonos',          'audifonos',          'TECNOLOGIA', now()),
    (gen_random_uuid(), 'Cargadores',         'cargadores',         'TECNOLOGIA', now()),
    (gen_random_uuid(), 'Cables de cargador', 'cables-de-cargador', 'TECNOLOGIA', now()),
    (gen_random_uuid(), 'Power banks',        'power-banks',        'TECNOLOGIA', now()),
    (gen_random_uuid(), 'Consolas',           'consolas',           'TECNOLOGIA', now()),
    (gen_random_uuid(), 'Parlantes',          'parlantes',          'TECNOLOGIA', now()),
    (gen_random_uuid(), 'Computadores',       'computadores',       'TECNOLOGIA', now()),
    (gen_random_uuid(), 'Tablets',            'tablets',            'TECNOLOGIA', now()),
    (gen_random_uuid(), 'Proyectores',        'proyectores',        'TECNOLOGIA', now())
on conflict (slug) do nothing;

