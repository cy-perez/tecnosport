-- La etiqueta de diseno que el lector de fotos le dio a un borrador de un album (11 de octubre de
-- 2026).
--
-- Un album de La Riverah trae un SKU por foto, y desde el 10 de octubre cada SKU es un borrador: la
-- misma camiseta en crema, cafe y negro son tres. El negocio decidio mantener eso y que quien revisa
-- los una en el panel. Para sugerirle cuales, el borrador guarda la etiqueta que el lector le dio al
-- diseno de sus fotos; dos de la misma publicacion con la misma etiqueta son, posiblemente, el mismo
-- diseno. Nula en los de antes y fuera de un album.
alter table borrador_producto add column diseno varchar(120);
