-- El lugar de cada mensaje dentro de lo que se registro de una vez (3 de octubre de 2026).
--
-- WhatsApp en Android exporta la hora sin segundos y todo el lote se guarda con el mismo
-- `creado_en`, asi que los mensajes de un mismo minuto empataban en las dos columnas del orden y
-- Postgres los devolvia como le tocara. El agrupador decide de que precio es una foto por quien
-- tiene antes y quien despues: con el empate revuelto, la misma exportacion podia armar
-- publicaciones distintas. Es el indice en la lista que recibe `guardarTodos`, que llega en el
-- orden del archivo.
--
-- Los mensajes de antes quedan en 0: sus lotes ya se agruparon y no se vuelven a agrupar.
alter table mensaje_proveedor add column posicion integer not null default 0;

drop index ix_mensaje_proveedor_lote;
create index ix_mensaje_proveedor_lote
    on mensaje_proveedor (lote_id, enviado_en, creado_en, posicion);
