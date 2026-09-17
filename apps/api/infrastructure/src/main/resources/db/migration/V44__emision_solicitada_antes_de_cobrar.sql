-- La emisión se escribe antes de que la plataforma cobre (adr/0033, revisión del 17/09/2026).
--
-- V43 dejó la fila naciendo con los identificadores que devolvía la plataforma, o sea DESPUÉS del
-- cobro. Entre el cobro y la respuesta no había fila, que es exactamente el hueco que la tabla
-- existía para tapar: un reinicio ahí dejaba una guía pagada sin nada que la nombre, y con ella se
-- perdía el id_tarifa, que es lo ÚNICO que la recupera por idempotencia dentro de las 96 horas.

-- Quién comprometió el saldo. Estaba solo en una línea de registro, y para una acción que gasta
-- dinero eso no es auditoría. `desconocido` para las filas que nacieron antes de esta columna: son
-- de desarrollo y no tiene sentido inventarles un responsable.
alter table emision_de_guia add column actor varchar(120) not null default 'desconocido';
alter table emision_de_guia alter column actor drop default;

-- El índice ahora cubre los tres estados en los que puede haber plata comprometida sin desenlace,
-- no solo EN_CURSO:
--
--   SOLICITADA     se pidió y todavía no se sabe nada. Es el estado nuevo, y el que hace que el
--                  segundo clic choque ANTES de que haya nada que pagar.
--   EN_CURSO       la plataforma cobró y creó los envíos; falta el número de guía.
--   INDETERMINADA  la llamada no terminó y PUEDE haber cobrado. No se reintenta sola: la resuelve
--                  una persona mirando el panel de la transportadora con el id_tarifa de la fila.
drop index uq_emision_en_curso_por_pedido;

create unique index uq_emision_abierta_por_pedido
    on emision_de_guia (pedido_id)
    where estado in ('SOLICITADA', 'EN_CURSO', 'INDETERMINADA');

-- La tarea busca por estado y por antigüedad en dos consultas distintas —las que puede releer y las
-- que se quedaron sin respuesta—, y las dos ordenan por solicitada_en.
drop index ix_emision_de_guia_estado;
create index ix_emision_de_guia_estado on emision_de_guia (estado, solicitada_en);

comment on column emision_de_guia.actor is
    'Quien pidio la emision y comprometio el saldo. En la fila y no solo en el registro: esto '
    'gasta dinero.';

comment on column emision_de_guia.estado is
    'SOLICITADA, EN_CURSO, INDETERMINADA, EMITIDA, FALLIDA o PARCIAL. Los tres primeros cuentan '
    'como abierta y bloquean una emision nueva del mismo pedido: en los tres puede haber plata '
    'comprometida sin desenlace. INDETERMINADA y PARCIAL piden ojo humano.';
