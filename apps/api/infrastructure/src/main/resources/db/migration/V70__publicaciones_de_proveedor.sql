-- Las publicaciones: un producto tal como el proveedor lo anuncio, armado a partir de los
-- mensajes de un lote (30 de septiembre de 2026). Segunda parte del contexto `proveedores`.

create table publicacion_proveedor (
    id uuid primary key,
    proveedor_id uuid not null references proveedor (id) on delete restrict,
    -- Las publicaciones son del lote y no del proveedor: rehacer la agrupacion con otra ventana
    -- produce otras publicaciones sobre los mismos mensajes.
    lote_id uuid not null references lote_ingesta (id) on delete restrict,
    mensaje_principal_id uuid not null references mensaje_proveedor (id) on delete restrict,
    -- La fecha del mensaje principal, que es la fecha del anuncio.
    fecha timestamptz not null,
    estado varchar(30) not null,
    -- Por que se descarto o por que fallo. El agregado lo exige en esos dos estados.
    motivo text,
    creado_en timestamptz not null,
    constraint ck_publicacion_proveedor_motivo check (
        estado not in ('DESCARTADA', 'ERROR') or motivo is not null
    )
);

create index ix_publicacion_proveedor_lote on publicacion_proveedor (lote_id, fecha);

-- Los mensajes que componen la publicacion, con su papel y su orden: los textos que siguieron al
-- principal y las fotos. Una tabla y no dos: son la misma relacion con un discriminador, y lo que
-- se consulta siempre es "todo lo de esta publicacion, en orden".
create table publicacion_mensaje (
    publicacion_id uuid not null references publicacion_proveedor (id) on delete cascade,
    rol varchar(10) not null,
    orden integer not null,
    mensaje_id uuid not null references mensaje_proveedor (id) on delete restrict,
    primary key (publicacion_id, rol, orden),
    constraint ck_publicacion_mensaje_rol check (rol in ('TEXTO', 'MEDIO'))
);
