-- La ingesta de proveedores cambia lo que guarda de cada borrador (3 de octubre de 2026).
--
-- 1. Las caracteristicas, una por linea, se vuelven la descripcion: el extractor ahora redacta el
--    texto que la ficha va a mostrar y el panel lo exige para aprobar. Las listas que ya estaban
--    guardadas pasan tal cual, una frase por linea, para que quien revise las convierta.
-- 2. El titulo en ingles que propone el extractor, para el texto alternativo de las fotos.
-- 3. "Bodi" es como se escribe en espanol: el tipo BODY se renombra a BODI.
-- 4. Las fotos que quien revisa saca del borrador, una por linea: la foto es de la publicacion y
--    otro borrador del mismo mensaje puede usarla, asi que no se borra, solo deja de ofrecerse.
alter table borrador_producto rename column caracteristicas to descripcion;

alter table borrador_producto add column alt_en varchar(200);

update borrador_producto set tipo = 'BODI' where tipo = 'BODY';

alter table borrador_producto add column fotos_descartadas text;
