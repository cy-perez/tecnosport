-- Los proveedores que mandan su surtido por WhatsApp, y los lotes y mensajes que entran de ahi
-- (30 de septiembre de 2026). Primera parte del contexto `proveedores`: las publicaciones, los
-- borradores y lo que cambia en `producto` llegan en las migraciones siguientes, cada una con la
-- fase que la necesita.

create table proveedor (
    id uuid primary key,
    nombre varchar(120) not null,
    -- La linea es un valor de LineaCatalogo y el agregado solo admite BOLSOS y ROPA. Sin `check`
    -- aqui: el dia que entre otra linea la regla cambia en el dominio, no en un `alter table`.
    linea varchar(20) not null,
    telefono_whatsapp varchar(30) not null,
    -- El nombre con que WhatsApp escribe al proveedor delante de cada mensaje, que es el del
    -- contacto en el celular del negocio. Es lo que identifica sus mensajes en una exportacion.
    nombre_en_exportacion varchar(120) not null,
    activo boolean not null,
    -- Se persiste y todavia no se usa: publicar solo lo decide una persona en esta iteracion.
    publicacion_automatica boolean not null,
    -- Nulo quiere decir "el de su linea", que vive en configuracion. numeric y no double: es un
    -- factor que multiplica dinero.
    factor_de_margen numeric(6,3),
    creado_en timestamptz not null,
    actualizado_en timestamptz not null
);

create table lote_ingesta (
    id uuid primary key,
    origen varchar(20) not null,
    -- `restrict` y no `cascade`: un proveedor con lotes no se borra, se desactiva. Los lotes son
    -- la cuenta de lo que entro y esa cuenta no desaparece con el contacto.
    proveedor_id uuid not null references proveedor (id) on delete restrict,
    -- La key del archivo original en el bucket privado. Nula solo en un lote que no vino de un
    -- archivo, que hoy no existe pero el origen CLOUD_API ya esta declarado.
    referencia_archivo text,
    estado varchar(20) not null,
    -- El resumen desarmado en columnas, todas nulas hasta que el lote termina. Son nueve enteros
    -- que el panel muestra tal cual; una columna json habria escondido en texto lo que se consulta
    -- como numero ("cuantos borradores dejo el lote de ayer").
    resumen_mensajes_leidos integer,
    resumen_mensajes_ignorados integer,
    resumen_mensajes_nuevos integer,
    resumen_publicaciones integer,
    resumen_borradores_nuevos integer,
    resumen_renovaciones integer,
    resumen_agotados integer,
    resumen_descartes integer,
    resumen_alertas integer,
    detalle_error text,
    creado_en timestamptz not null,
    iniciado_en timestamptz,
    terminado_en timestamptz,
    -- Un lote terminado trae resumen y uno en error trae motivo. El agregado ya lo exige; el
    -- `check` es para el dia que alguien escriba la fila por otro camino.
    constraint ck_lote_ingesta_cerrado check (
        (estado <> 'TERMINADO' or resumen_mensajes_leidos is not null)
        and (estado <> 'ERROR' or detalle_error is not null)
    )
);

-- La lista del panel: los lotes de un proveedor, el mas reciente primero.
create index ix_lote_ingesta_proveedor on lote_ingesta (proveedor_id, creado_en desc);

create table mensaje_proveedor (
    id uuid primary key,
    proveedor_id uuid not null references proveedor (id) on delete restrict,
    lote_id uuid not null references lote_ingesta (id) on delete restrict,
    -- El id de WhatsApp cuando exista, o el hash de proveedor, fecha y contenido que fabrica el
    -- registro para una exportacion. Unico por proveedor: es lo que hace inofensivo subir dos
    -- veces el mismo archivo. `IdExternoDeMensaje.LARGO_MAXIMO` son 200.
    id_externo varchar(200) not null,
    enviado_en timestamptz not null,
    tipo varchar(10) not null,
    -- Tal cual llego, sin limpiar: es el material de la extraccion y esa se puede repetir.
    texto text,
    pie_de_foto text,
    -- La key de la foto en el bucket privado. Nula en un texto y en una imagen omitida.
    referencia_archivo text,
    -- true cuando la exportacion se hizo sin archivos y de la foto solo queda la marca.
    medio_omitido boolean not null,
    creado_en timestamptz not null,
    constraint uq_mensaje_proveedor_id_externo unique (proveedor_id, id_externo)
);

-- Los mensajes de un lote en orden de envio, que es como se agrupan en publicaciones.
create index ix_mensaje_proveedor_lote on mensaje_proveedor (lote_id, enviado_en);
