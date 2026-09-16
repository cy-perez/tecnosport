-- La emisión de la guía (adr/0033).
--
-- Skydropx cobra antes de que exista la guía: POST /shipments responde 202 con
-- payment_status=paid y master_tracking_number=null, y el número aparece entre veinticinco
-- segundos y varios minutos después — o no aparece nunca y el envío muere en workflow_status=error
-- (docs/13-skydropx-capacidades.md §6.10). Sin esta tabla, un reinicio del servicio entre el cobro
-- y la respuesta deja una guía pagada que nadie sabe que existe.

create table emision_de_guia (
    id uuid primary key,
    pedido_id uuid not null references pedido (id),
    -- El nombre visible de la transportadora. Se guarda porque la respuesta del envío no lo trae:
    -- trae carrier_name, que es el código de la plataforma ("ninetynineminutes"), y quien resuelve
    -- la emisión minutos después ya no tiene a mano la tarifa de donde salió el nombre.
    transportadora varchar(120) not null,
    -- La tarifa con la que se emitió. Es además la llave de idempotencia de Skydropx:
    -- unique_shipment cachea la respuesta por rate_id durante 96 horas, así que repetir la
    -- creación con esta misma tarifa devuelve los mismos envíos en vez de cobrar dos veces.
    id_tarifa varchar(120) not null,
    estado varchar(20) not null,
    detalle text,
    solicitada_en timestamptz not null,
    resuelta_en timestamptz
);

-- Los identificadores de envío que devolvió la plataforma. Son varios cuando el pedido va en
-- varios bultos: la tarifa pasa a multishipment y crea un envío —y una guía, y un cobro— por bulto
-- (adr/0031). En tabla aparte y no como arreglo para poder buscarlos uno a uno: es lo que garantiza
-- que nada de lo que se pagó se pierde de vista, incluso cuando la emisión sale PARCIAL y no llega
-- a haber despacho.
create table envio_en_plataforma (
    emision_id uuid not null references emision_de_guia (id),
    posicion int not null,
    id_externo varchar(120) not null,
    primary key (emision_id, posicion),
    constraint uq_envio_en_plataforma_externo unique (id_externo)
);

-- Un pedido no puede tener dos emisiones abiertas al mismo tiempo: la segunda cobraría otra vez
-- por lo mismo. Parcial y no simple, porque sí puede tener varias resueltas — un fallo se
-- reintenta, y la plataforma reembolsa el intento muerto.
create unique index uq_emision_en_curso_por_pedido
    on emision_de_guia (pedido_id)
    where estado = 'EN_CURSO';

create index ix_emision_de_guia_estado on emision_de_guia (estado, solicitada_en);

comment on table emision_de_guia is
    'Un intento de emitir las guias de un pedido. Existe porque la plataforma cobra antes de que '
    'haya guia: entre el 202 y el numero hay plata comprometida y nada que mostrar (adr/0033).';

comment on column emision_de_guia.estado is
    'EN_CURSO, EMITIDA, FALLIDA o PARCIAL. PARCIAL es multienvio con unas guias vivas y otras '
    'muertas: pide ojo humano porque hay guias pagadas que alguien tiene que cancelar o usar.';
