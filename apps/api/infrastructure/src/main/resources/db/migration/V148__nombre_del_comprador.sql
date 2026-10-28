-- El nombre de quien compra (adr/0099).
--
-- El pedido solo tenía el de quien recibe (V36), y desde el saludo de los correos (adr/0098) ese
-- era el nombre con el que se le escribía a quien compró: un regalo le llegaba al comprador
-- saludando al destinatario. El checkout pide ahora los dos, y cuando son la misma persona manda
-- el mismo nombre dos veces.
--
-- Nula a propósito, por la misma razón que el contacto en V36: los pedidos que ya existen no lo
-- tienen y se reconstruyen tal como quedaron. La exigencia para un pedido nuevo vive en CrearPedido.
alter table pedido
    add column nombre_comprador varchar(120);

comment on column pedido.nombre_comprador is
    'Nombre de quien compra, a quien se le escribe. Nulo en pedidos anteriores a adr/0099.';
