-- Las fotos que quien revisa sube a un borrador desde el panel (6 de octubre de 2026).
--
-- La ingesta puede dejar un borrador sin fotos: una exportacion sin adjuntos, o un proveedor que
-- manda el precio en un mensaje y las fotos en otro lote. Antes, ese borrador no se podia aprobar.
--
-- Son del borrador y no de la publicacion: un mensaje con varios productos da varios borradores
-- sobre la misma publicacion, y la foto que alguien sube para uno no es de los otros. Por eso no
-- son filas de mensaje_proveedor —ese guarda lo que el proveedor mando, tal como llego— y se van
-- con el borrador (on delete cascade). El archivo vive en el bucket privado del proveedor, bajo
-- proveedores/{proveedorId}/borradores/{borradorId}/, y lo borra quien borra el borrador.
create table borrador_foto_subida (
    id uuid primary key,
    borrador_id uuid not null references borrador_producto (id) on delete cascade,
    referencia_archivo text not null,
    subida_en timestamptz not null
);

create index ix_borrador_foto_subida_borrador on borrador_foto_subida (borrador_id, subida_en);

-- Dos filas sobre el mismo archivo romperian el borrado: quitar una se llevaria el archivo de la
-- otra. El caso de uso lo consulta antes; esto cubre dos confirmaciones a la vez.
create unique index ux_borrador_foto_subida_archivo on borrador_foto_subida (referencia_archivo);
