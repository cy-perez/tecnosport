-- Lo que se eligio de la variante, congelado en la linea del pedido (3 de octubre de 2026).
--
-- Desde que la vitrina ofrece tallas, el SKU solo no le dice a quien despacha que talla empacar.
-- "Negro · M", con los valores de los atributos de la variante en el momento de comprar: si el
-- catalogo cambia despues, el pedido sigue diciendo lo que se compro. Nulo en las lineas de antes.
-- `text` y no un varchar con tope: cada valor de atributo admite 120 caracteres y una variante
-- puede tener varios, y un tope aqui convertiria un nombre largo en un pedido que no se crea.
alter table linea_pedido add column detalle_variante text;
