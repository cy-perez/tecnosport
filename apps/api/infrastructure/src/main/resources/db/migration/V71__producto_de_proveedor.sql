-- Lo que un producto lleva de mas cuando viene de un proveedor, y los borradores que esperan
-- revision (30 de septiembre de 2026). Tercera parte del contexto `proveedores`.

-- Origen y disponibilidad son dos ejes distintos del ya existente `estado`: publicar y retirar
-- de la vitrina lo decide una persona (ADR-0051); esto lo dicen los mensajes del proveedor y el
-- tiempo. Todo lo que existe hoy es MANUAL y DISPONIBLE, y el job de disponibilidad nunca toca
-- un manual.
alter table producto
    add column origen varchar(20) not null default 'MANUAL',
    add column proveedor_id uuid references proveedor (id) on delete restrict,
    add column precio_proveedor numeric(14,2),
    -- sha256 de proveedor, titulo normalizado y precio del proveedor. 64 hex, como imagen.hash.
    add column huella_proveedor varchar(64),
    add column visto_por_ultima_vez timestamptz,
    add column estado_disponibilidad varchar(30) not null default 'DISPONIBLE';

-- Sin `default` de aqui en adelante: el codigo escribe siempre las dos columnas, y un default
-- que se quede es una segunda verdad sobre que es un producto nuevo.
alter table producto
    alter column origen drop default,
    alter column estado_disponibilidad drop default;

-- Un producto de proveedor lleva proveedor, huella y ultima vista; uno manual, nada de eso. Lo
-- exige el agregado y lo repite la base para el dia que alguien escriba la fila por otro camino.
alter table producto
    add constraint ck_producto_origen check (
        (origen = 'PROVEEDOR'
            and proveedor_id is not null
            and huella_proveedor is not null
            and visto_por_ultima_vez is not null)
        or (origen = 'MANUAL'
            and proveedor_id is null
            and huella_proveedor is null
            and precio_proveedor is null)
    );

-- La huella es unica por proveedor: es lo que reconoce un anuncio repetido. Parcial porque los
-- manuales no tienen y un unico sobre nulos no protegeria nada.
create unique index uq_producto_huella_proveedor
    on producto (proveedor_id, huella_proveedor)
    where huella_proveedor is not null;

-- La consulta del job de disponibilidad: los de proveedor que siguen disponibles, por ultima
-- vista. Parcial por lo mismo: los manuales y los ya ocultos no se buscan nunca.
create index ix_producto_vencimiento
    on producto (visto_por_ultima_vez)
    where origen = 'PROVEEDOR' and estado_disponibilidad = 'DISPONIBLE';

create table borrador_producto (
    id uuid primary key,
    publicacion_id uuid not null references publicacion_proveedor (id) on delete restrict,
    proveedor_id uuid not null references proveedor (id) on delete restrict,
    -- El JSON tal cual lo devolvio el extractor: lo que deja ver que dijo el modelo antes de que
    -- nadie lo tocara, y comparar cuando cambie el prompt.
    extraccion_cruda text not null,
    titulo varchar(200),
    linea varchar(20),
    tipo varchar(30) not null,
    precio_proveedor numeric(14,2),
    precio_venta_sugerido numeric(14,2),
    tallas_tipo varchar(20) not null,
    tallas_sirve_hasta varchar(20),
    -- Listas cortas de texto, una por linea. No son datos que se consulten sueltos: se leen
    -- enteros con el borrador y los edita el panel.
    tallas_valores text,
    cantidad_tonos integer,
    tonos_nombrados text,
    material varchar(200),
    caracteristicas text,
    huella varchar(64),
    -- 64 bits en 16 hex. Nulo cuando la publicacion no trajo foto legible.
    phash varchar(16),
    -- Los nombres de AlertaBorrador separados por coma. Se leen enteros, nunca se filtran por uno.
    alertas varchar(300) not null,
    estado varchar(30) not null,
    -- `set null`: si el producto se borra del catalogo, la constancia de que se aprobo se queda.
    producto_id uuid references producto (id) on delete set null,
    motivo_rechazo text,
    creado_en timestamptz not null,
    actualizado_en timestamptz not null,
    -- Solo el rechazo se sostiene con un check: un APROBADO nace con producto, pero el producto
    -- se puede borrar despues y el `set null` de arriba dejaria la fila sin el, a proposito.
    constraint ck_borrador_producto_rechazado check (
        estado <> 'RECHAZADO' or motivo_rechazo is not null
    )
);

-- La bandeja del panel: por estado y proveedor, el mas reciente primero.
create index ix_borrador_producto_bandeja on borrador_producto (estado, proveedor_id, creado_en desc);

-- Las huellas visuales de lo que ya es producto de este proveedor, para reconocer una foto
-- repetida con otro texto. Parcial: solo los que terminaron en producto y tenian foto.
create index ix_borrador_producto_phash
    on borrador_producto (proveedor_id)
    where producto_id is not null and phash is not null;
