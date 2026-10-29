-- La marca de lo que no es de marca: la ropa, el calzado y los bolsos de los proveedores llegan
-- con ella propuesta (29 de octubre de 2026). Existia en la nube porque se creo a mano desde el
-- panel; en local y en un ambiente nuevo no. Se compara sin tilde para no duplicar una "Generica".
insert into marca (id, nombre, creado_en)
select gen_random_uuid(), 'Genérica', now()
 where not exists (
       select 1 from marca where lower(translate(nombre, 'éÉ', 'eE')) = 'generica'
 );
