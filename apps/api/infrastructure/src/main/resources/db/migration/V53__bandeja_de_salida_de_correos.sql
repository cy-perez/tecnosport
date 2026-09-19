-- La bandeja de salida de los correos transaccionales (adr/0045).
--
-- Hasta aquí, el correo se mandaba dentro de la transacción de quien lo pedía y ANTES del commit.
-- Un fallo al comprometer dejaba a quien compró leyendo "reintegramos el dinero de tu pedido" y al
-- sistema sin ninguna constancia de ese reintegro. Ningún catch arregla ese sentido: hay que
-- invertir el orden. Ahora el caso de uso escribe una fila aquí —con su misma transacción— y una
-- tarea la manda después. Si la transacción revierte, la fila se va con ella.
create table correo_pendiente
(
    id                 uuid        primary key,
    destinatario       text        not null,
    asunto             text        not null,
    cuerpo_html        text        not null,
    creado_en          timestamptz not null,
    -- Cuándo toca el siguiente intento. Arranca igual que creado_en —el primer intento es
    -- inmediato— y la reclama la tarea empujándolo hacia adelante, que es a la vez el reclamo
    -- entre instancias y el espaciado de los reintentos.
    proximo_intento_en timestamptz not null,
    intentos           integer     not null default 0,
    enviado_en         timestamptz,
    ultimo_error       text,

    constraint ck_correo_pendiente_intentos check (intentos >= 0)
);

comment on table correo_pendiente is
    'Bandeja de salida de correos transaccionales. La fila se escribe con la transacción de la operación que la origina, y una tarea la manda después con reintentos (adr/0045).';

comment on column correo_pendiente.cuerpo_html is
    'Dato personal en reposo: lleva el nombre de quien compró, su pedido y a veces su dirección. Las filas ya enviadas se purgan a los treinta días (docs/08-seguridad-legal.md).';

comment on column correo_pendiente.proximo_intento_en is
    'Cuándo se puede volver a intentar. Lo empuja el reclamo de la tarea, nunca nadie más.';

comment on column correo_pendiente.ultimo_error is
    'Por qué no salió la última vez. Va en la tabla y nunca en un registro: un rechazo de SMTP suele repetir la dirección.';

-- Índice parcial: la tarea busca exactamente las filas sin enviar cuyo próximo intento ya venció, y
-- ésas son pocas y siempre recientes. Uno sobre la tabla entera crecería con todo el histórico de
-- correos ya mandados para responder una consulta que nunca los mira.
create index idx_correo_pendiente_enviable
    on correo_pendiente (proximo_intento_en, creado_en)
    where enviado_en is null;

-- La purga sí mira las enviadas, y por su fecha de envío.
create index idx_correo_pendiente_purga
    on correo_pendiente (enviado_en)
    where enviado_en is not null;
