-- Si el lote es el chat de caballero de un proveedor que publica en dos (9 de octubre de 2026).
--
-- Meraki publica desde el mismo numero en un chat general y en uno de caballero, y el general
-- repite anuncios del otro. Se sabe al leer el archivo, por el nombre del chat: el zip se guarda en
-- el bucket con un nombre aleatorio. Lo que el chat general repite del de caballero se descarta, y
-- lo que ya dejo en revision se rechaza cuando llega el de caballero.
--
-- Los lotes de antes quedan en false: nadie los leyo con esta regla.
alter table lote_ingesta add column chat_de_caballero boolean not null default false;
