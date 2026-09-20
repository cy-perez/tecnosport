-- El unico de marca.nombre deja de distinguir mayusculas (19 de septiembre de 2026).
--
-- V54 creo marca_nombre_unico sobre la columna tal cual, y con las marcas entrando por migracion
-- eso bastaba: las doce las escribio una persona de una sola vez y ninguna se repite.
--
-- Desde hoy las marcas se crean desde el panel (ADR-0047), y un formulario abierto a alguien con
-- prisa mete "xiaomi" un martes y "Xiaomi" un jueves. Para la base de V54 son dos filas distintas,
-- y el dano es exactamente el que V54 describe para el duplicado exacto: los productos repartidos
-- entre las dos marcas, el filtro de la vitrina ofreciendo "Xiaomi" dos veces y cada una con media
-- marca detras.
--
-- Se reemplaza en vez de agregarse al lado porque el unico sobre lower(nombre) implica al otro:
-- dos nombres exactamente iguales tambien tienen el mismo lower. Dejar los dos seria pagar dos
-- indices por una sola garantia.
drop index marca_nombre_unico;

create unique index marca_nombre_unico on marca (lower(nombre));

-- Sin unaccent: comparar "Sony" con "Sony" acentuado exigiria la extension, que es una dependencia
-- nueva del esquema. Queda fuera a proposito y no por descuido.
--
-- Si alguna base ya tuviera "Xiaomi" y "xiaomi", esto falla y hay que limpiarla a mano. Es lo
-- correcto, y es el mismo criterio que eligio V54: fallar ruidoso al migrar es mejor que arrastrar
-- el duplicado hasta la vitrina.
