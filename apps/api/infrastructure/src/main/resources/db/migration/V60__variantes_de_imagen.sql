-- Una imagen se publica en varios anchos, y cada ancho es una fila (adr/0057).
--
-- Hasta hoy una imagen tenía una sola URL publicada y, al lado, `url_webp`: una columna que nació
-- esperando una conversión de formato que iba a hacer el asistente de captura de la Fase 5 y que
-- nunca existió. Siempre apuntó al mismo objeto que `url`, y desde ADR-0056 además guarda la URL
-- de un AVIF, así que el nombre dice un formato que no es. No se renombra: se sustituye. Con las
-- variantes en su propia tabla, la columna no tiene ningún trabajo que hacer.
--
-- El otro motivo es el `srcset`: la portada carga el AVIF de 1200 px para pintarlo en un hueco de
-- 180 —211 KiB según `image-delivery-insight`—, y para dejar que el navegador elija hace falta
-- saber qué anchos existen de cada imagen. No son los mismos para todas: el procesamiento de
-- estudio no amplía, así que hay tomas con 2000, 1600, 1200, 800 y 480 y tomas que solo llegan a
-- 480. La escalera es un dato de cada imagen, no una constante del sistema.
create table variante_imagen
(
    id        uuid primary key,
    imagen_id uuid    not null references imagen_producto (id) on delete cascade,
    ancho     integer not null,
    url       text    not null,
    bytes     bigint  not null,
    constraint uq_variante_imagen_ancho unique (imagen_id, ancho),
    constraint ck_variante_imagen_positivos check (ancho > 0 and bytes > 0)
);

-- `on delete cascade` y no un borrado en dos pasos desde el repositorio: una fila de imagen se
-- borra desde tres sitios distintos —el reemplazo de la principal, el descarte de una foto de
-- galería y el borrado de un set de rotación entero—, y bastaría con que uno olvidara las
-- variantes para dejar filas huérfanas que nadie volvería a mirar.

-- Cada imagen que ya existe se convierte en su propia variante única. Después de esto toda imagen
-- tiene al menos una, que es lo que el agregado exige: el sitio sigue sirviendo exactamente lo que
-- servía, con un `srcset` de una sola entrada, y los anchos de verdad llegan cuando se vuelvan a
-- subir las fotos.
insert into variante_imagen (id, imagen_id, ancho, url, bytes)
select gen_random_uuid(), i.id, i.ancho, i.url, i.bytes
from imagen_producto i;

-- El JPEG para los previsualizadores de enlaces —WhatsApp, Facebook—, que no negocian formatos y
-- con AVIF no muestran nada. Nulable: hoy no hay ninguno subido, y una imagen sin vista previa es
-- una imagen que se ve bien en el sitio y no se ve en una tarjeta de enlace, no un dato roto.
--
-- No es un respaldo del `<img>` del navegador: el sitio no envuelve las imágenes en `<picture>`
-- (ADR-0056) y el nombre evita que alguien lo suponga.
alter table imagen_producto
    add column url_vista_previa text;

alter table imagen_producto
    drop column url_webp;

-- `imagen_producto.url`, `ancho` y `bytes` se quedan, y ahora son la variante mayor repetida. Es
-- una copia a propósito, decidida al planear esto: la leen el `og:image`, la línea del carrito y el
-- panel, y derivarla obligaría a que los tres caminos cargaran las variantes. No puede envejecer
-- como envejeció `variante.existencia` (adr/0050), porque no hay nadie que la escriba aparte: sale
-- del mismo agregado, que la calcula de sus variantes cada vez que guarda.
