-- Pedido (Fase 3). Ver docs/02-modelo-datos.md.

-- Direccion es un objeto de valor: columnas aplanadas en pedido, no una
-- tabla propia. Todas nulas cuando tipo_entrega = RETIRO_EN_PUNTO.
create table pedido (
    id uuid primary key,
    usuario_id uuid,
    correo varchar(255) not null,
    tipo_entrega varchar(20) not null,
    codigo_dane_departamento varchar(10),
    departamento varchar(120),
    codigo_dane_ciudad varchar(10),
    ciudad varchar(120),
    direccion text,
    indicaciones text,
    metodo_pago varchar(30) not null,
    estado varchar(30) not null,
    creado_en timestamptz not null default now()
);

-- Congelada al crear el pedido (docs/02): nombre, sku, precio y tasa de IVA
-- no se releen del catálogo. Sin FK a variante por la misma razón que
-- linea_carrito no la tiene: el precio histórico no depende de que la
-- variante siga existiendo.
create table linea_pedido (
    id uuid primary key,
    pedido_id uuid not null references pedido (id) on delete cascade,
    variante_id uuid not null,
    sku varchar(60) not null,
    nombre varchar(200) not null,
    cantidad integer not null,
    precio_unitario numeric(14, 2) not null,
    tasa_iva numeric(5, 4) not null,
    imagen_url text
);

create index ix_linea_pedido_pedido on linea_pedido (pedido_id);

-- Un registro por transición, nunca se sobrescribe (docs/00-producto.md).
create table historial_pedido (
    id uuid primary key,
    pedido_id uuid not null references pedido (id) on delete cascade,
    estado varchar(30) not null,
    fecha timestamptz not null,
    actor varchar(255) not null,
    motivo text
);

create index ix_historial_pedido_pedido on historial_pedido (pedido_id);
