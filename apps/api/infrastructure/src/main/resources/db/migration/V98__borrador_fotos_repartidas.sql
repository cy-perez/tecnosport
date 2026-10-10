-- Lo que la ingesta lee en las fotos y en los textos de La Riverah y Violeta (10 de octubre de 2026).
--
-- Las cuatro columnas son JSON en text, como el resto de listas del borrador: no se consultan por
-- dentro, solo se leen enteras con el borrador. Nulas en los borradores de antes, que no tenian nada
-- de esto y se siguen aprobando igual.
alter table borrador_producto
    -- Las tallas de cada tono cuando el mensaje no las tiene todas en todos:
    -- [{"tono":"cocoa","tallas":["ML"]}]. La aprobacion no crea el cocoa SM.
    add column tallas_por_tono text,
    -- Los precios del anuncio que no son del producto: la gorra, el set, la promo por cantidad.
    -- [{"concepto":"Gorra","precio":35000}]. Solo para que quien revisa los vea.
    add column precios_adicionales text,
    -- El color que la lectura de fotos vio en cada foto de un solo color, por mensaje:
    -- {"<uuid>":"negro"}. Lo que el panel propone al aprobar.
    add column tonos_sugeridos text,
    -- El JSON que devolvio la lectura de fotos, tal cual, como extraccion_cruda para el texto.
    add column lectura_de_fotos text;
