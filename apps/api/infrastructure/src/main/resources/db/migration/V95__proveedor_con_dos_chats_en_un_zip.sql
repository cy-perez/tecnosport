-- Si el proveedor sube sus dos chats -el general y el de caballero- en un solo zip
-- (9 de octubre de 2026).
--
-- Meraki publica desde el mismo numero en los dos, y el general repite anuncios del de caballero.
-- Con un solo zip, de dos `.txt` -`Meraki.txt` y `MerakiMen.txt`-, el servidor encola dos lotes en
-- orden, el de caballero primero, y el general descarta lo que aquel ya trajo. El panel valida la
-- estructura del zip antes de dejarlo subir. Todos los demas, en false.
alter table proveedor add column dos_chats_en_un_zip boolean not null default false;
