-- El rastro del paquete, tal como lo cuenta la transportadora (adr/0022).
--
-- Append-only: aquí no se actualiza ni se borra nada. Es lo mismo que hace historial_pedido con
-- las transiciones del pedido y por el mismo motivo — el día de la reclamación hay que poder
-- decir qué se supo y cuándo.
create table evento_seguimiento (
    id uuid primary key,
    envio_id uuid not null references envio (id),
    estado varchar(30) not null,
    descripcion text,
    -- Dos instantes y no uno, y no es redundancia: ocurrio_en es cuando la transportadora dice
    -- que pasó, recibido_en es cuando nos enteramos. No coinciden. Un webhook perdido y
    -- recuperado por la conciliación llega días después del hecho, y confundirlos haría parecer
    -- que el paquete se movió cuando lo único que se movió fue la noticia.
    ocurrio_en timestamptz not null,
    recibido_en timestamptz not null,
    -- El identificador del evento en la plataforma. Es lo que hace idempotente el webhook: un
    -- reintento trae el mismo y la restricción lo rechaza en la base, no solo en el dominio.
    id_externo varchar(120) not null,
    constraint uq_evento_seguimiento_externo unique (envio_id, id_externo)
);

-- Se lee siempre por envío y en orden de ocurrencia: es lo que pinta el seguimiento público y lo
-- que mira la conciliación para saber si hace falta preguntarle a la transportadora.
create index ix_evento_seguimiento_envio on evento_seguimiento (envio_id, ocurrio_en);

comment on column evento_seguimiento.id_externo is
    'Identificador del evento en Skydropx. Hace idempotente el webhook: el mismo evento '
    'reintentado no se guarda dos veces (adr/0022).';

comment on column evento_seguimiento.recibido_en is
    'Cuando nos llego la noticia, que no es cuando ocurrio. La conciliacion recupera eventos '
    'perdidos dias despues del hecho.';
