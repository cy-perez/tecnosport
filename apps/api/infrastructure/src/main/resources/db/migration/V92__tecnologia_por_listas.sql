-- La tecnologia entra por la lista de precios del proveedor, que procesa la skill
-- `listas-de-proveedor`, y no por la exportacion del chat (ADR-0075, 8 de octubre de 2026).

-- Un modelo de la lista esperando a que una persona elija que colores se venden de cada
-- configuracion y a que precio. Un borrador por modelo y proveedor en revision a la vez: la
-- siguiente lista actualiza ese, no abre otro.
create table borrador_tecnologia (
    id uuid primary key,
    proveedor_id uuid not null references proveedor (id) on delete restrict,
    -- El id que decide la skill ("samsung-galaxy-a17-5g"): estable entre listas.
    id_modelo varchar(120) not null,
    huella varchar(64) not null,
    titulo varchar(200) not null,
    -- Como texto, no como ids: la skill no conoce la base y quien aprueba elige las del catalogo.
    marca_sugerida varchar(80),
    categoria_sugerida varchar(80),
    descripcion text not null,
    meta_descripcion varchar(320),
    -- Los colores oficiales del modelo, uno por linea. Se leen enteros con el borrador.
    paleta text,
    -- `set null`: si el producto se borra del catalogo, la constancia de que se aprobo se queda.
    producto_id uuid references producto (id) on delete set null,
    estado varchar(30) not null,
    motivo_rechazo text,
    visto_en timestamptz not null,
    creado_en timestamptz not null,
    actualizado_en timestamptz not null,
    constraint ck_borrador_tecnologia_estado check (
        estado in ('EN_REVISION', 'APROBADO', 'RECHAZADO')
    ),
    constraint ck_borrador_tecnologia_rechazado check (
        estado <> 'RECHAZADO' or motivo_rechazo is not null
    )
);

create unique index ux_borrador_tecnologia_en_revision
    on borrador_tecnologia (proveedor_id, id_modelo)
    where estado = 'EN_REVISION';

-- La bandeja del panel y los resueltos de un modelo, que la importacion consulta por cada uno.
create index ix_borrador_tecnologia_bandeja on borrador_tecnologia (estado, creado_en desc);
create index ix_borrador_tecnologia_modelo on borrador_tecnologia (proveedor_id, id_modelo);

-- Las configuraciones del borrador, en el orden de la lista.
create table borrador_tecnologia_configuracion (
    borrador_id uuid not null references borrador_tecnologia (id) on delete cascade,
    orden integer not null,
    sku varchar(60) not null,
    titulo varchar(200) not null,
    ram varchar(20),
    almacenamiento varchar(20),
    sim varchar(20),
    costo_proveedor numeric(14,2) not null,
    precio_mercado numeric(14,2),
    colores_sugeridos text,
    colores_elegidos text,
    precio_venta numeric(14,2),
    -- Sin un unico por (borrador_id, sku), y a proposito: Hibernate reescribe esta coleccion por
    -- posicion (`@OrderColumn`), y cuando una lista quita una configuracion del medio escribe un sku
    -- en una posicion mientras sigue en otra. Un unico no diferible tumbaba la importacion entera.
    -- Que no se repita lo sostiene el agregado (`BorradorTecnologia.configuracionesValidas`).
    primary key (borrador_id, orden)
);

-- De que configuracion de la lista sale cada variante, y su costo de hoy. El costo no es el
-- precio de venta y nunca sale al comprador. Se va con la variante.
create table variante_de_proveedor (
    variante_id uuid primary key references variante (id) on delete cascade,
    producto_id uuid not null references producto (id) on delete cascade,
    proveedor_id uuid not null references proveedor (id) on delete restrict,
    configuracion varchar(60) not null,
    color varchar(80) not null,
    costo numeric(14,2) not null,
    actualizado_en timestamptz not null
);

create index ix_variante_de_proveedor_configuracion
    on variante_de_proveedor (proveedor_id, configuracion);
create index ix_variante_de_proveedor_producto on variante_de_proveedor (producto_id);

-- Las listas que ya entraron. Repetir la misma volvia a reponer lo vendido entre las dos, y una
-- mas vieja deshacia la de hoy: `ImportarListaDeTecnologia` mira aqui antes de mover nada. La
-- huella es el SHA-256 del contenido, asi que una lista corregida el mismo dia si entra.
create table lista_tecnologia_importada (
    proveedor_id uuid not null references proveedor (id) on delete cascade,
    huella varchar(64) not null,
    fecha_lista date not null,
    importada_en timestamptz not null,
    primary key (proveedor_id, huella)
);

create index ix_lista_tecnologia_importada_fecha
    on lista_tecnologia_importada (proveedor_id, fecha_lista desc);

-- Los ejes de una variante de tecnologia, ademas del Color de V72. `AprobarBorradorTecnologia`
-- los busca por nombre sin distinguir mayusculas. Mismo criterio que V72: dato que toda
-- instalacion necesita, solo si no existen.
insert into atributo (id, nombre, tipo)
select gen_random_uuid(), 'RAM', 'TEXTO'
where not exists (select 1 from atributo where lower(nombre) = 'ram');

insert into atributo (id, nombre, tipo)
select gen_random_uuid(), 'Almacenamiento', 'TEXTO'
where not exists (select 1 from atributo where lower(nombre) = 'almacenamiento');

insert into atributo (id, nombre, tipo)
select gen_random_uuid(), 'SIM', 'TEXTO'
where not exists (select 1 from atributo where lower(nombre) = 'sim');
