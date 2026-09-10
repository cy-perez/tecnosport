-- La constancia del dinero devuelto, sacada de dentro de solicitud_retracto.
--
-- Vivía como columnas de esa fila, y el argumento era bueno: así un reembolso sin solicitud era
-- imposible de escribir. Lo que lo invalidó fue contar cuántos caminos de los términos publicados
-- terminan en devolver dinero — retracto, garantía, reversión del pago, no disponibilidad
-- sobrevenida y plazo de entrega incumplido, cinco y no uno. Con la constancia dentro del primero,
-- los otros cuatro habrían acabado con su propia copia, y "cuánto devolvimos el mes pasado" no se
-- puede responder sumando cinco tablas a mano.
create table reintegro (
    id uuid primary key,
    pedido_id uuid not null references pedido (id),
    -- RETRACTO, GARANTIA, REVERSION, NO_DISPONIBILIDAD, PLAZO_INCUMPLIDO. No es una etiqueta de
    -- reporte: cada uno nace de una obligación distinta, con su plazo y su disparador.
    motivo varchar(30) not null,
    -- La solicitud que lo justifica, o el propio pedido en los caminos que no nacen de una
    -- solicitud del comprador. Sin llave foránea porque apunta a tablas distintas según el motivo;
    -- lo que sí se exige es que nunca esté vacío: un reintegro sin nada detrás es plata que salió
    -- sin explicación.
    origen_id uuid not null,
    monto numeric(14, 2) not null,
    medio varchar(30) not null,
    comprobante varchar(120),
    registrado_en timestamptz not null,
    registrado_por varchar(120) not null,

    constraint ck_reintegro_monto check (monto > 0)
);

create index ix_reintegro_pedido on reintegro (pedido_id, registrado_en desc);

-- Una constancia por origen. Hoy es la verdad —la máquina de estados de la solicitud solo admite
-- un cierre— y dejarlo escrito impide que un doble clic en el panel deje dos constancias del mismo
-- hecho. Un reintegro parcial en varios tramos exigiría levantar este índice a propósito.
create unique index ux_reintegro_origen on reintegro (origen_id);

insert into reintegro (
    id, pedido_id, motivo, origen_id, monto, medio, comprobante, registrado_en, registrado_por
)
select
    gen_random_uuid(),
    s.pedido_id,
    'RETRACTO',
    s.id,
    s.reembolso_monto,
    s.reembolso_medio,
    s.reembolso_comprobante,
    s.reembolso_registrado_en,
    s.reembolso_registrado_por
from solicitud_retracto s
where s.reembolso_monto is not null;

alter table solicitud_retracto add column reintegro_id uuid references reintegro (id);

update solicitud_retracto s set reintegro_id = r.id from reintegro r where r.origen_id = s.id;

-- La invariante no se relaja, cambia de lado: una solicitud que se declara reembolsada exige el id
-- de su constancia. Es lo que sustituye a la imposibilidad estructural que daba tenerla dentro.
alter table solicitud_retracto drop constraint ck_retracto_reembolsada;

alter table solicitud_retracto
    drop column reembolso_monto,
    drop column reembolso_medio,
    drop column reembolso_comprobante,
    drop column reembolso_registrado_en,
    drop column reembolso_registrado_por;

alter table solicitud_retracto add constraint ck_retracto_reembolsada check (
    estado <> 'REEMBOLSADA' or reintegro_id is not null
);
