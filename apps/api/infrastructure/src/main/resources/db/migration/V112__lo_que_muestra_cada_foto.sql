-- Lo que muestra cada foto, ademas de su propia variante (12 de octubre de 2026, ADR-0082).
--
-- 1. `en_la_ficha`: la principal que vale para todos los tonos no entra en la galeria de la ficha
--    (ADR-0069). Cuando todas las fotos de un producto retratan varios tonos juntos -la blusa
--    negra, cocoa y verde de frente y de espalda-, eso dejaba la foto que quien aprueba eligio de
--    principal sin verse en la ficha. Quien aprueba lo pide con una casilla; por omision, lo de
--    antes. Solo la principal la lleva: las demas entran siempre.
alter table imagen_producto add column en_la_ficha boolean not null default false;

alter table imagen_producto add constraint ck_imagen_producto_en_la_ficha check (
    not en_la_ficha or tipo = 'PRINCIPAL'
);

-- 2. `muestra_variantes`: las variantes sin foto propia que la foto tambien retrata, en orden y
--    separadas por coma. La camiseta en cinco tonos repartidos en dos fotos: elegir el negro lleva
--    a la foto del blanco y el negro. Hasta hoy una foto colgaba de una sola variante
--    (`variante_id`), y ADR-0081 lo dejo escrito como lo que faltaba.
--
--    Texto y no tabla por lo mismo que la muestra de color (V80) y las listas del borrador (V71):
--    se lee entera con la imagen y nunca se filtra por una de sus partes. Una variante borrada
--    deja su id aqui sin nada que lo encuentre, y la ficha lo ignora: no hay nada que mostrar.
--    Nula en lo que ya estaba.
alter table imagen_producto add column muestra_variantes text;
