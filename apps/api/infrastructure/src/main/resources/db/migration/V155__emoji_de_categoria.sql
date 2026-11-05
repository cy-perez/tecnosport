-- El emoji que abre el pie de cada publicacion en redes (5 de noviembre de 2026, ADR-0107).
--
-- Nulo significa "usa el de tu rama", como la escala de tallas (V75): Camisas no necesita el suyo
-- si Ropa > Dama ya tiene uno, y una categoria sin emoji ni en ella ni en su rama publica con el
-- de reserva. Por eso la columna es nullable y sin valor por omision: el codigo anterior no la
-- conoce, la ignora al leer y no la escribe al guardar, y convive con ella hasta que produccion
-- libere el que si la usa.
--
-- 16 caracteres y no 1 ni 2: un emoji compuesto --bandera, tono de piel, union con ZWJ-- son
-- varios puntos de codigo. Lo mismo que `Categoria.LARGO_MAXIMO_DEL_EMOJI`, que cuenta unidades
-- UTF-16 y por tanto nunca pasa de 16 puntos de codigo, que es lo que cuenta `varchar` aqui.
alter table categoria add column emoji varchar(16);

comment on column categoria.emoji is
    'Emoji que abre el pie de las publicaciones de sus productos; nulo usa el de la categoria padre.';

-- ---------------------------------------------------------------------------------------------
-- Los iniciales, por slug.
-- ---------------------------------------------------------------------------------------------
--
-- Solo los slugs que crean las migraciones (V38, V62, V63, V64, V75, V84, V108): los que deja
-- `SembradorCatalogo` en una base de desarrollo no estan en produccion y no se nombran. Un slug
-- que una base no tenga actualiza cero filas, que es lo correcto. Y solo donde la columna sigue
-- vacia, para que volver a correr esto a mano no pise lo que alguien escribio en el panel.
--
-- Las ramas de calzado y de bolsos llevan el suyo y sus hojas lo heredan; Morrales lo reemplaza.
-- Ropa no lleva uno en la rama: una camiseta y una blusa no se anuncian con el mismo, y lo que no
-- tiene hoja propia sale con el de reserva hasta que alguien lo elija en el panel.
update categoria c set emoji = e.emoji
from (values
    ('calzado-dama',              '👟'),
    ('calzado-caballero',         '👟'),
    ('calzado-unisex',            '👟'),
    ('bolsos-dama',               '👜'),
    ('bolsos-caballero',          '👜'),
    ('bolsos-dama-morrales',      '🎒'),
    ('bolsos-caballero-morrales', '🎒'),
    ('celulares',                 '📱'),
    ('relojes',                   '⌚'),
    ('audifonos',                 '🎧'),
    ('consolas',                  '🎮'),
    ('parlantes',                 '🔊'),
    ('computadores',              '💻'),
    ('tablets',                   '📱'),
    ('proyectores',               '📽️'),
    ('ropa-caballero-camisetas',  '👕'),
    ('ropa-dama-camisas',         '👔'),
    ('ropa-dama-blusas',          '👚'),
    ('ropa-dama-jeans',           '👖'),
    ('ropa-caballero-jeans',      '👖'),
    ('ropa-dama-pantalones',      '👖')
) as e (slug, emoji)
where c.slug = e.slug
  and c.emoji is null;
