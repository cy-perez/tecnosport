-- Radicacion de peticiones, quejas, reclamos y solicitudes de datos personales.
--
-- Los terminos publicados prometen "canales de orientacion, asistencia y radicacion de peticiones,
-- quejas y reclamos", y radicar es mas que leer un correo: es dejar constancia con un numero, con
-- una fecha y con un plazo corriendo. Hasta ahora no habia ninguna de las tres cosas, asi que
-- demostrar que se respondio en plazo era imposible en las dos direcciones — y quien tiene la carga
-- de probar que cumplio es el responsable.

-- Contador atomico por anio, mismo mecanismo que secuencia_pedido: un unico
-- "insert ... on conflict ... returning" reclama el siguiente sin bloqueo pesimista explicito.
create table secuencia_radicado (
    anio integer primary key,
    siguiente bigint not null default 1
);

create table solicitud_atencion (
    id uuid primary key,
    numero_radicado varchar(24) not null unique,
    -- PETICION, QUEJA, RECLAMO, CONSULTA_DATOS, RECLAMO_DATOS, GARANTIA, REVERSION. De el depende
    -- que reloj corre: el mismo buzon recibe solicitudes con plazos legales distintos.
    tipo varchar(20) not null,
    correo varchar(320) not null,
    -- Opcional: una consulta de datos personales no tiene por que venir de una compra, y exigir un
    -- pedido convertiria un derecho de cualquier titular en un privilegio de clientes.
    pedido_id uuid references pedido (id),
    -- Las dos fechas, y hacen falta las dos. El plazo corre desde recibida_en; la distancia hasta
    -- radicada_en es lo unico que despues explica por que nadie se entero a tiempo.
    recibida_en timestamptz not null,
    radicada_en timestamptz not null,
    -- Siempre una persona del negocio ("admin:<id>"): la solicitud llega por correo o WhatsApp.
    radicada_por varchar(120) not null,
    asunto text not null,
    estado varchar(20) not null,

    -- La prorroga vale por el aviso, no por otorgarla: la ley concede dias extra si se informa al
    -- interesado con sus motivos antes de que venza el plazo inicial.
    prorroga_otorgada_en timestamptz,
    prorroga_otorgada_por varchar(120),
    prorroga_motivo text,
    prorroga_avisada_en timestamptz,

    respuesta_en timestamptz,
    respuesta_por varchar(120),
    respuesta_resumen text,

    constraint ck_atencion_recibida_antes check (recibida_en <= radicada_en),
    constraint ck_atencion_prorrogada check (
        estado <> 'PRORROGADA' or (
            prorroga_otorgada_en is not null
            and prorroga_otorgada_por is not null
            and prorroga_motivo is not null
            and prorroga_avisada_en is not null
        )
    ),
    constraint ck_atencion_respondida check (
        estado <> 'RESPONDIDA' or (
            respuesta_en is not null
            and respuesta_por is not null
            and respuesta_resumen is not null
        )
    )
);

-- La consulta de la bandeja: lo que falta por responder, lo mas viejo arriba. Mismo criterio que el
-- recaudo pendiente, y por el mismo motivo: lo que corre es un plazo.
create index ix_solicitud_atencion_abiertas on solicitud_atencion (estado, recibida_en);

-- Y la del pedido, para ver que se ha radicado sobre una compra concreta.
create index ix_solicitud_atencion_pedido on solicitud_atencion (pedido_id, recibida_en desc);
