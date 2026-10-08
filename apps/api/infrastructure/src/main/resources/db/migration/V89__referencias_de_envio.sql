-- Las referencias de envio: el peso promedio por categoria y las medidas de la bolsa (7 de octubre
-- de 2026, `ADR-0071`).
--
-- La ropa, el calzado y los bolsos no se miden uno por uno: se despachan en una bolsa plastica de
-- medidas fijas, con el peso que el negocio promedio para cada tipo de prenda. Hasta hoy una
-- variante sin medir solo se vendia con recogida (`ADR-0046`); con esto, las de esas tres lineas se
-- cotizan a domicilio con la bolsa de referencia. La tecnologia sigue igual: se mide con la ficha
-- del fabricante o va con recogida.
--
-- Las cifras las dio el negocio y se cambian desde el panel ("Pesos y medidas de envio"), sin
-- desplegar nada. Esta migracion solo siembra el punto de partida.

-- Una sola fila: las medidas son transversales a las tres lineas. El `check (id = 1)` es lo que la
-- hace unica -- sin el, un segundo `insert` dejaria dos bolsas y nadie sabria cual vale.
create table envio_medidas_referencia (
    id       smallint primary key check (id = 1),
    largo_cm integer not null check (largo_cm > 0),
    ancho_cm integer not null check (ancho_cm > 0),
    alto_cm  integer not null check (alto_cm > 0)
);

-- Un peso por categoria hoja. `on delete cascade` porque borrar una categoria (que `EliminarCategoria`
-- solo permite si esta vacia) no tiene por que tropezar con su promedio: sin productos, el peso no
-- lo lee nadie.
create table envio_peso_referencia (
    categoria_id uuid primary key references categoria (id) on delete cascade,
    peso_gramos  integer not null check (peso_gramos > 0)
);

-- 40 x 30 x 10 cm. El negocio las dio como ancho 30, largo 40 y alto 10.
insert into envio_medidas_referencia (id, largo_cm, ancho_cm, alto_cm)
values (1, 40, 30, 10);

-- Los pesos por slug, porque el id de una categoria no es el mismo en desarrollo que en la nube. Un
-- slug que no exista en esta base simplemente no inserta: esa categoria aparece en el panel sin peso
-- y se le pone desde ahi.
--
-- `ropa-dama-falta-short` va junto a `ropa-dama-falda-short`: en la nube la categoria se creo como
-- "Falta - Short", con la errata tambien en el slug. Se siembran las dos grafias para que el peso
-- quede puesto exista la que exista.
insert into envio_peso_referencia (categoria_id, peso_gramos)
select c.id, p.peso_gramos
from categoria c
join (values
    -- Ropa > Caballero
    ('ropa-caballero-busos', 400),
    ('ropa-caballero-camisas', 300),
    ('ropa-caballero-camisetas', 300),
    ('ropa-caballero-jeans', 700),
    ('ropa-caballero-pantalonetas', 300),
    ('ropa-caballero-polos', 300),
    ('ropa-caballero-sudaderas', 400),
    -- Ropa > Dama
    ('ropa-dama-blusas', 300),
    ('ropa-dama-bodis', 600),
    ('ropa-dama-busos', 400),
    ('ropa-dama-camisas', 300),
    ('ropa-dama-chaquetas', 700),
    ('ropa-dama-conjunto', 700),
    ('ropa-dama-faldas', 300),
    ('ropa-dama-falda-short', 400),
    ('ropa-dama-falta-short', 400),
    ('ropa-dama-jeans', 700),
    ('ropa-dama-licras', 300),
    ('ropa-dama-pantalones', 700),
    ('ropa-dama-shorts', 300),
    ('ropa-dama-sudaderas', 400),
    -- Calzado deportivo
    ('calzado-dama', 700),
    ('calzado-caballero', 700),
    ('calzado-unisex', 700),
    -- Bolsos
    ('bolsos-caballero-morrales', 1000),
    ('bolsos-dama-bolsos-de-mano', 800),
    ('bolsos-dama-manos-libres', 800),
    ('bolsos-dama-morrales', 800)
) as p (slug, peso_gramos) on p.slug = c.slug;
