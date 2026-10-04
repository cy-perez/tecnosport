-- Combinaciones de colores y patrones en la muestra (4 de octubre de 2026).
--
-- Una variante puede ser de hasta tres colores -una camiseta negra y roja es "Negro / Rojo"- y su
-- circulo se pinta por porciones en el orden en que se eligieron. Y la paleta gana tres disenos
-- que no son un color: multicolor, estampado y animal print, cada uno con su patron y los colores
-- que lo dibujan.

-- ---------------------------------------------------------------------------------------------
-- 1. Los patrones de la paleta.
-- ---------------------------------------------------------------------------------------------
--
-- `hex` sigue siendo obligatorio: en un patron es su primer color, el que guarda `color_hex` en
-- la variante para quien no sabe de patrones. Los colores del patron van en orden, separados por
-- coma, y el `check` exige los dos campos o ninguno.
alter table color_paleta
    add column patron varchar(20),
    add column colores_patron text;

alter table color_paleta add constraint ck_color_paleta_patron check (
    (patron is null and colores_patron is null)
    or (patron in ('MULTICOLOR', 'ESTAMPADO', 'ANIMAL_PRINT')
        and colores_patron ~ '^#[0-9A-F]{6}(,#[0-9A-F]{6})+$')
);

insert into color_paleta (id, nombre, nombre_en, hex, orden, patron, colores_patron) values
    (gen_random_uuid(), 'Multicolor',   'Multicolour',  '#E53935', 0, 'MULTICOLOR',
        '#E53935,#FB8C00,#FDD835,#43A047,#1E88E5,#8E24AA'),
    (gen_random_uuid(), 'Estampado',    'Printed',      '#F5F5F5', 0, 'ESTAMPADO',
        '#F5F5F5,#1B2A4A'),
    (gen_random_uuid(), 'Animal print', 'Animal print', '#C19A6B', 0, 'ANIMAL_PRINT',
        '#C19A6B,#3B2A1A')
on conflict do nothing;

-- El mismo orden alfabetico de V78, con los tres nuevos dentro.
update color_paleta c set orden = o.posicion
from (
    select id,
           row_number() over (
               order by translate(lower(nombre), 'áéíóúüñ', 'aeiouun'), nombre
           ) as posicion
    from color_paleta
) o
where c.id = o.id;

-- ---------------------------------------------------------------------------------------------
-- 2. La muestra de cada valor de color.
-- ---------------------------------------------------------------------------------------------
--
-- Las porciones en su orden, separadas por punto y coma; cada una es un HEX, o el patron con sus
-- colores: "#111111;#C62828", "ANIMAL_PRINT:#C19A6B,#3B2A1A;#111111". Texto y no tabla por lo
-- mismo que las listas del borrador (V71): se lee entera con la variante y nunca se filtra por
-- una porcion. Nula en lo que ya estaba: ahi la muestra es `color_hex` solo.
alter table variante_atributo_valor add column muestra text;
