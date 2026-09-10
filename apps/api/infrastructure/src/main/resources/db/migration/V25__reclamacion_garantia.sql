-- Reclamaciones de garantia legal (Ley 1480 de 2011, arts. 7 a 18).
--
-- Tabla propia y no columnas de solicitud_atencion: una reclamacion de garantia tiene datos que
-- ninguna otra solicitud tiene —que variante fallo, desde cuando corre el termino, cual de las tres
-- salidas se eligio— y meterlos en el agregado generico llenaria de nulos a las peticiones y las
-- quejas.
create table reclamacion_garantia (
    id uuid primary key,
    -- La solicitud de atencion que la contiene, con su radicado y su plazo de respuesta. Radicar
    -- una garantia radica tambien su solicitud: para el comprador es una sola cosa.
    solicitud_id uuid not null unique references solicitud_atencion (id),
    pedido_id uuid not null references pedido (id),
    -- Sin llave foranea a variante: el catalogo puede cambiar y una reclamacion no puede quedarse
    -- sin poder escribirse porque alguien retiro el producto.
    variante_id uuid not null,
    -- El termino corre desde aqui.
    entregado_en timestamptz not null,
    radicada_en timestamptz not null,
    -- Congelado al radicar, y nulo cuando ese dia nadie sabia el termino de esa categoria — hoy es
    -- el caso de los celulares, con [[GARANTIA DE CELULARES]] todavia pendiente. Nulo no significa
    -- "sin garantia": significa que el sistema no puede afirmar que vencio.
    meses_de_termino integer,
    descripcion_del_fallo text not null,
    estado varchar(20) not null,
    -- REPARACION, REPOSICION o REINTEGRO: las tres salidas de la ley, no solo la del dinero.
    desenlace varchar(20),
    resuelta_en timestamptz,
    resuelta_por varchar(120),
    -- Solo con desenlace REINTEGRO, y obligatorio con el: una garantia cerrada "devolviendo el
    -- dinero" sin constancia de que salio es lo que la ley pide poder demostrar.
    reintegro_id uuid references reintegro (id),

    constraint ck_garantia_termino check (meses_de_termino is null or meses_de_termino > 0),
    constraint ck_garantia_resuelta check (
        estado <> 'RESUELTA' or (
            desenlace is not null and resuelta_en is not null and resuelta_por is not null
        )
    ),
    constraint ck_garantia_reintegro check (
        (desenlace = 'REINTEGRO' and (estado <> 'RESUELTA' or reintegro_id is not null))
        or (desenlace is distinct from 'REINTEGRO' and reintegro_id is null)
    )
);

-- La consulta del panel es siempre "que garantias tiene este pedido".
create index ix_reclamacion_garantia_pedido on reclamacion_garantia (pedido_id, radicada_en desc);
