-- Los sets de La Riverah y la falda short (12 de octubre de 2026).
--
-- La Riverah anuncia la prenda con su acompanante: "Falda $49.900 / Set falda + basica $88.000",
-- "Camiseta $65.000 / Gorra $35.000 / Duo $95.000". El negocio decidio que, para ella, el borrador
-- sea el set y no la prenda sola: la falda con la blusa es un conjunto, la camiseta o el polo con
-- la gorra es un set que se publica en Camisetas o en Polos. El costo es el del set si lo anuncia,
-- y si no la suma de las prendas.

-- Quien vende sets. Solo La Riverah; los demas siguen publicando la prenda sola. Se busca por
-- nombre porque el id no es el mismo en desarrollo que en la nube, y si no existe no pasa nada:
-- se marca desde el panel.
alter table proveedor add column arma_sets boolean not null default false;
-- Solo si entra por el chat: el dominio no deja armar sets a la tecnologia, y una fila asi no
-- se podria leer.
update proveedor set arma_sets = true
where lower(nombre) like '%riverah%' and linea <> 'TECNOLOGIA';

-- Con que prenda se armo el set del borrador y de donde salio su costo. Nulos en los de antes y en
-- los que son la prenda sola.
alter table borrador_producto add column set_acompanante varchar(20);
alter table borrador_producto add column set_origen_del_costo varchar(20);
alter table borrador_producto add constraint ck_borrador_set_completo
    check ((set_acompanante is null) = (set_origen_del_costo is null));

-- Lo que un producto pesa de mas sobre el promedio de su categoria: la gorra de un set, 100 g.
alter table producto add column peso_adicional_gramos integer not null default 0
    check (peso_adicional_gramos >= 0);

-- La errata de la nube: la categoria se creo como "Falta - Short", con la errata tambien en el
-- slug (ver V89). Se corrige si la buena no existe ya; su peso de referencia va por id y la sigue.
update categoria
set slug = 'ropa-dama-falda-short', nombre = 'Falda - Short'
where slug = 'ropa-dama-falta-short'
  and not exists (select 1 from categoria where slug = 'ropa-dama-falda-short');

-- Donde no existe ninguna de las dos -una base nueva-, se crea, para que el panel tenga donde
-- proponer la falda short. Sin escala de tallas propia: hereda la de su rama, como Conjuntos (V100).
insert into categoria (id, nombre, slug, linea, padre_id, creado_en)
select gen_random_uuid(), 'Falda - Short', 'ropa-dama-falda-short', p.linea, p.id, now()
from categoria p
where p.slug = 'ropa-dama'
  and not exists (select 1 from categoria where slug = 'ropa-dama-falta-short')
on conflict (slug) do nothing;

-- Los pesos de referencia que faltaban (dados por el negocio el 12 de octubre de 2026): un conjunto
-- de dama y uno de caballero pesan 700 g. Si alguien ya les puso otro desde el panel, manda este:
-- es el dato que el negocio dio para estas dos hojas, al reves que la V108, que respetaba lo puesto.
insert into envio_peso_referencia (categoria_id, peso_gramos)
select c.id, 700
from categoria c
where c.slug in ('ropa-dama-conjuntos', 'ropa-caballero-conjuntos')
on conflict (categoria_id) do update set peso_gramos = excluded.peso_gramos;

-- La falda short recien creada, con los 400 g que V89 ya le daba a las dos grafias. La que ya
-- existia conserva el suyo.
insert into envio_peso_referencia (categoria_id, peso_gramos)
select c.id, 400
from categoria c
where c.slug = 'ropa-dama-falda-short'
on conflict (categoria_id) do nothing;
