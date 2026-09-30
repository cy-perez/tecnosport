-- Los dos atributos con los que la ingesta de proveedores arma las variantes (30 de septiembre
-- de 2026): `Color`, de tipo COLOR, para los tonos que el proveedor nombra, y `Talla`, de tipo
-- TEXTO, para la lista de tallas. `AprobarBorrador` los busca por nombre sin distinguir
-- mayusculas y sin ellos responde ATRIBUTO_DE_CATALOGO_NO_DEFINIDO. No hay endpoint que los cree
-- desde el panel: en local y dev los pone `SembradorCatalogo` --que solo corre sobre una base sin
-- productos-- y en produccion no los pone nadie. Son dato real que toda instalacion necesita, y
-- eso entra por migracion, como el arbol de categorias (V63).
--
-- Con la mayuscula inicial del sembrador, que es como el panel los muestra. Sin valores
-- permitidos: la lista de tonos y tallas la dicta cada anuncio. Solo si no existen, comparando
-- sin mayusculas, porque una base ya sembrada no debe acabar con dos ejes `Color`.
-- gen_random_uuid() como en V38: aqui no hay dominio que genere el UUID v7.
insert into atributo (id, nombre, tipo)
select gen_random_uuid(), 'Color', 'COLOR'
where not exists (select 1 from atributo where lower(nombre) = 'color');

insert into atributo (id, nombre, tipo)
select gen_random_uuid(), 'Talla', 'TEXTO'
where not exists (select 1 from atributo where lower(nombre) = 'talla');
