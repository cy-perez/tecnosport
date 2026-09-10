-- Solicitudes de reversion del pago (Ley 1480 de 2011, art. 51).
--
-- Tabla propia y no un estado mas del pedido: la reversion no es un retracto con otro nombre. El
-- retracto no necesita motivo y lo resuelve el comercio solo; la reversion tiene causales tasadas e
-- involucra al emisor del medio de pago. Meter las dos en el mismo sitio pierde las causales, que es
-- justo lo que decide a quien le toca responder.
create table solicitud_reversion (
    id uuid primary key,
    solicitud_id uuid not null unique references solicitud_atencion (id),
    pedido_id uuid not null references pedido (id),
    -- FRAUDE, PRODUCTO_NO_ENTREGADO, PRODUCTO_NO_CORRESPONDE, PRODUCTO_DEFECTUOSO. No hay una
    -- quinta: son tasadas.
    causal varchar(30) not null,
    -- Cuando el comprador tuvo noticia del hecho. De aqui cuelga el plazo de cinco dias habiles que
    -- los terminos publicados le imponen a el, distinto del plazo de respuesta que corre contra
    -- nosotros desde que llega su mensaje.
    fecha_del_hecho timestamptz not null,
    radicada_en timestamptz not null,
    -- Foto de lo que se sabia el dia de radicar, congelada igual que en el retracto. INDETERMINADO
    -- existe porque sin festivos cargados no se puede afirmar que un plazo vencio.
    verdicto_plazo varchar(20) not null,
    estado varchar(20) not null,

    -- "Nosotros facilitamos el tramite", dicen los terminos. Sin este texto la promesa seria
    -- indemostrable, que es la forma mas silenciosa de incumplirla.
    gestionada_en timestamptz,
    gestionada_por varchar(120),
    gestion text,

    desenlace varchar(30),
    resuelta_en timestamptz,
    -- Solo cuando el dinero salio de aqui. Si revierte el emisor, la plata vuelve por la red de
    -- pagos y este sistema no movio un peso.
    reintegro_id uuid references reintegro (id),

    constraint ck_reversion_hecho_antes check (fecha_del_hecho <= radicada_en),
    constraint ck_reversion_gestionada check (estado <> 'GESTIONADA' or gestion is not null),
    constraint ck_reversion_resuelta check (
        estado <> 'RESUELTA' or (desenlace is not null and resuelta_en is not null)
    ),
    constraint ck_reversion_reintegro check (
        (desenlace = 'REINTEGRADO_DIRECTAMENTE' and (estado <> 'RESUELTA' or reintegro_id is not null))
        or (desenlace is distinct from 'REINTEGRADO_DIRECTAMENTE' and reintegro_id is null)
    )
);

create index ix_solicitud_reversion_pedido on solicitud_reversion (pedido_id, radicada_en desc);
