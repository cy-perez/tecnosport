-- Las fotos de otras publicaciones que se suman a un borrador (10 de octubre de 2026): la misma
-- referencia que el proveedor vuelve a publicar con otra foto, o una que quien revisa mueve aqui
-- desde otro borrador. Ids de mensaje separados por salto de linea, en orden, como
-- fotos_descartadas. Sin llave: los mensajes de otro lote se pueden borrar con su lote, y el
-- borrador los deja de pintar sin romperse.
alter table borrador_producto add column fotos_agregadas text;
