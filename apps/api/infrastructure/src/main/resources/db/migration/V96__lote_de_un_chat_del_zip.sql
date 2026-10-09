-- Cual de los dos chats del zip lee el lote, cuando el proveedor sube sus dos chats juntos
-- (9 de octubre de 2026).
--
-- Una subida de Meraki deja dos lotes sobre el mismo archivo: CABALLERO lee `MerakiMen.txt` y se
-- procesa primero; GENERAL lee `Meraki.txt` despues, y descarta lo que aquel ya trajo. Nula en el
-- lote de un zip de un solo chat, que son todos los demas y todos los de antes.
alter table lote_ingesta add column chat_del_zip varchar(10);

alter table lote_ingesta add constraint ck_lote_ingesta_chat_del_zip
    check (chat_del_zip is null or chat_del_zip in ('CABALLERO', 'GENERAL'));
