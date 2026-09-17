-- El barrio de la dirección de entrega.
--
-- Es el `area_level3` de la plataforma de envíos, el mismo campo que en el origen es obligatorio
-- para que la recolección se programe (docs/13 §6.10). En el destino no condiciona el precio —eso
-- sale del código DANE— ni bloquea nada: mejora la dirección que se imprime en la guía.
--
-- Anulable a propósito, y no solo por los pedidos anteriores: el checkout lo pide sin exigirlo.
-- Un campo obligatorio que alguien no sabe llenar se rellena con cualquier cosa, y eso impreso en
-- una guía es peor que vacío.
alter table pedido add column barrio varchar(120);

comment on column pedido.barrio is
    'area_level3 de la plataforma de envios. Opcional: mejora la entrega, no condiciona el precio.';
