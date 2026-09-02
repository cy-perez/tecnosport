-- Catálogo de solo lectura (Fase 1). Ver docs/02-modelo-datos.md.
-- Ningún dato de negocio aquí: los datos de siembra los inserta
-- SembradorCatalogo (@Profile("local")), nunca una migración.

create extension if not exists pg_trgm;

create table marca (
    id uuid primary key,
    nombre varchar(120) not null,
    creado_en timestamptz not null default now()
);

create table categoria (
    id uuid primary key,
    nombre varchar(120) not null,
    slug varchar(160) not null,
    linea varchar(30) not null,
    creado_en timestamptz not null default now(),
    constraint ux_categoria_slug unique (slug)
);

create table atributo (
    id uuid primary key,
    nombre varchar(120) not null,
    tipo varchar(20) not null,
    creado_en timestamptz not null default now()
);

-- Valores permitidos de un atributo (lista libre si está vacía). No es un
-- agregado propio: @ElementCollection de AtributoJpaEntity.
create table atributo_valor_permitido (
    atributo_id uuid not null references atributo (id),
    valor varchar(120) not null,
    primary key (atributo_id, valor)
);

create table producto (
    id uuid primary key,
    nombre varchar(200) not null,
    slug varchar(220) not null,
    descripcion text not null default '',
    marca_id uuid not null references marca (id),
    categoria_id uuid not null references categoria (id),
    estado varchar(20) not null,
    creado_en timestamptz not null default now(),
    actualizado_en timestamptz not null default now(),
    constraint ux_producto_slug unique (slug)
);

-- Búsqueda de texto libre por nombre, nunca LIKE '%...%' (docs/02).
-- lower(): la búsqueda es insensible a mayúsculas, ver RepositorioProductosJpa.
create index ix_producto_nombre_trgm on producto using gin (lower(nombre) gin_trgm_ops);
create index ix_producto_categoria on producto (categoria_id);
create index ix_producto_marca on producto (marca_id);
create index ix_producto_estado on producto (estado);

create table variante (
    id uuid primary key,
    producto_id uuid not null references producto (id),
    sku varchar(60) not null,
    precio numeric(14, 2) not null,
    tasa_iva numeric(5, 4) not null,
    existencia integer not null default 0,
    codigo_barras varchar(60),
    estado varchar(20) not null,
    creado_en timestamptz not null default now(),
    -- El dominio solo evita SKU duplicado dentro de un mismo producto en
    -- construcción (no ve el resto del catálogo); esta es la regla real de
    -- unicidad global, y solo se puede expresar aquí, con persistencia.
    constraint ux_variante_sku unique (sku)
);

create index ix_variante_producto on variante (producto_id);
-- Precio "desde" (MIN por producto) y filtro por rango: cubre ambos casos.
create index ix_variante_producto_precio on variante (producto_id, precio);

create table variante_atributo_valor (
    id uuid primary key,
    variante_id uuid not null references variante (id),
    atributo_id uuid not null references atributo (id),
    valor varchar(120) not null,
    color_hex varchar(7),
    constraint ux_variante_atributo unique (variante_id, atributo_id)
);

create table set_rotacion (
    id uuid primary key,
    producto_id uuid not null references producto (id),
    variante_id uuid references variante (id),
    estado varchar(20) not null,
    capturado_por varchar(120),
    capturado_en timestamptz,
    dispositivo varchar(120),
    version_asistente varchar(60)
);

create index ix_set_rotacion_producto on set_rotacion (producto_id);

create table imagen_producto (
    id uuid primary key,
    producto_id uuid not null references producto (id),
    variante_id uuid references variante (id),
    set_rotacion_id uuid references set_rotacion (id),
    tipo varchar(20) not null,
    orden integer not null,
    url text not null,
    url_webp text not null,
    ancho integer not null,
    alto integer not null,
    bytes bigint not null,
    hash varchar(80) not null,
    alt_es varchar(300) not null default '',
    alt_en varchar(300) not null default '',
    creada_en timestamptz not null default now()
);

create index ix_imagen_producto_producto on imagen_producto (producto_id);
create index ix_imagen_producto_set_rotacion on imagen_producto (set_rotacion_id);

-- A lo sumo una imagen PRINCIPAL por producto (docs/02: "imagen principal obligatoria").
create unique index ux_imagen_principal_por_producto
    on imagen_producto (producto_id)
    where tipo = 'PRINCIPAL' and variante_id is null;
