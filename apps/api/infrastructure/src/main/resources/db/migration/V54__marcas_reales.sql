-- Las marcas que el negocio de verdad vende (19 de septiembre de 2026).
--
-- Hasta hoy las unicas marcas de la base eran "TecnoSport" y "Under Trail", las dos ficcion
-- declarada de SembradorCatalogo. Ninguna de las que aparecen en las listas del proveedor existia
-- en ninguna parte, y sin ellas el catalogo real no se puede cargar: POST /api/v1/admin/productos
-- exige un marcaId de una fila que ya exista, y no hay endpoint que cree marcas.
--
-- Van AQUI y no en el sembrador, por el mismo motivo que las categorias de V38 y con la misma
-- comprobacion detras: el sembrador solo corre cuando la tabla de productos esta vacia
-- (`if (productos.count() > 0) return;`), asi que nada de lo que se ponga ahi llega a una base que
-- ya tiene datos. Y por la razon de fondo, que es la que importa: el sembrador es ficcion
-- declarada; estas marcas son dato real del negocio. El dato real que toda instalacion necesita es
-- una migracion.

-- Primero el indice unico, y no es adorno para poder escribir `on conflict`.
--
-- `marca` se creo en V1 sin ninguna restriccion sobre el nombre, asi que hasta hoy la base admitia
-- dos marcas llamadas "Xiaomi" con identificadores distintos. Eso no es un duplicado inofensivo:
-- los productos se repartirian entre las dos, el filtro de la vitrina ofreceria "Xiaomi" dos veces
-- y cada una mostraria media marca. `categoria` ya tenia su unico sobre el slug desde V1; a `marca`
-- le faltaba el suyo.
--
-- Se descubrio escribiendo esta migracion: llevaba un `on conflict do nothing` sin columna, que sin
-- restriccion no protege de nada y solo aparenta hacerlo. Un guardian que no dispara da confianza
-- falsa, que es el error que este proyecto ya se comio con el plugin de capas.
--
-- Si alguna base ya tuviera nombres repetidos, esto falla y hay que limpiarla a mano. Es lo
-- correcto: fallar ruidoso al migrar es mejor que arrastrar el duplicado hasta la vitrina.
create unique index marca_nombre_unico on marca (nombre);

-- La lista sale del analisis de la lista del proveedor del 12 de septiembre de 2026
-- (catalogo/productos.json, 96 productos): estas son las doce marcas identificadas, con el numero
-- de productos de cada una al lado. No se inventa ninguna -- una marca que nadie vende no tiene
-- por que existir.
--
-- Se dan de alta las doce y no solo las del primer lote que se publique, y eso se puede hacer sin
-- ensuciar la vitrina desde hoy: ListarMarcas dejo de devolver `listarTodas()` y solo ofrece las
-- que tienen al menos un producto PUBLICADO, asi que una marca sin productos no aparece en ningun
-- filtro. Antes de ese cambio, esta migracion habria metido doce filtros vacios en la tienda.
--
-- gen_random_uuid() y no UUID v7 como el resto del sistema, igual que en V38: aqui no hay dominio
-- que los genere, y el orden temporal de un identificador solo importa donde se pagina por el. Una
-- marca se lista por nombre.
--
-- El nombre se escribe como lo escribe el fabricante, que es como lo busca quien compra: "JBL" y no
-- "Jbl", "TCL" y no "Tcl". Es un nombre propio y no pasa por Transloco.
insert into marca (id, nombre, creado_en) values
    (gen_random_uuid(), 'Xiaomi',   now()),  -- 38 productos
    (gen_random_uuid(), 'Samsung',  now()),  -- 18
    (gen_random_uuid(), 'JBL',      now()),  -- 14
    (gen_random_uuid(), 'Motorola', now()),  --  6
    (gen_random_uuid(), 'Honor',    now()),  --  6
    (gen_random_uuid(), 'Apple',    now()),  --  5
    (gen_random_uuid(), 'Lenovo',   now()),  --  2
    (gen_random_uuid(), 'TCL',      now()),  --  2
    (gen_random_uuid(), 'Realme',   now()),  --  1
    (gen_random_uuid(), 'Nintendo', now()),  --  1
    (gen_random_uuid(), 'Sony',     now()),  --  1
    (gen_random_uuid(), 'Bose',     now())   --  1
on conflict (nombre) do nothing;
