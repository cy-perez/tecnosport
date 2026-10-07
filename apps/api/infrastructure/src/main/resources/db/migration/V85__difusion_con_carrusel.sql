-- La difusion publica todas las fotos de la ficha, no solo la principal (6 de octubre de 2026).
--
-- `url_imagen` guardaba una sola URL porque un post era una foto. Desde hoy es un carrusel, y la
-- constancia tiene que decir **cuales** fotos salieron -- no cuales tiene el producto hoy, que
-- manana pueden ser otras. Ese era ya el motivo de guardar la URL en vez de mirar el producto.

-- Un `rename` y no una columna nueva: una fila vieja es exactamente un carrusel de una foto, asi
-- que el dato que hay ya es el dato que va. Anadir `urls_imagenes` dejando `url_imagen` habria
-- dejado la primera URL escrita en dos sitios, con nada que vigile que coinciden -- que es lo que
-- `ImagenProducto` evita derivando la URL de su variante mayor en vez de guardarla aparte.
alter table publicacion_en_red rename column url_imagen to urls_imagenes;

-- Texto con una URL por linea, como `categoria.escala_tallas` (V75) y por la misma razon: se lee
-- entera con la fila y nadie busca nunca una publicacion por una de sus fotos. El tipo ya era
-- `text not null`, asi que no hay nada mas que cambiar.
comment on column publicacion_en_red.urls_imagenes is
    'Las fotos que salieron en el post, una URL por linea y en el orden en que se publicaron.';
