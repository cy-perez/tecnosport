-- Dos estados nuevos de emision_de_guia, para cuando el pedido se cancela (adr/0033, y la decisión
-- 7 de docs/13-skydropx-capacidades.md §5).
--
-- No hay nada estructural que cambiar y eso conviene decirlo, porque la ausencia de un ALTER hace
-- dudar de si esta migración hace falta:
--
--   * `estado` es varchar(20) sin CHECK, así que los dos valores nuevos entran sin tocar el tipo.
--     ANULADA son 7 caracteres y SIN_ANULAR son 10.
--   * el índice único parcial uq_emision_abierta_por_pedido lista los tres estados abiertos
--     ('SOLICITADA', 'EN_CURSO', 'INDETERMINADA') y ninguno de los nuevos lo es: una emisión anulada
--     no puede bloquear una emisión nueva del pedido, porque el pedido ya está cancelado y no va a
--     pedir otra.
--   * ix_emision_de_guia_estado sigue sirviendo igual: la bandeja de revisión filtra por estado.
--
-- Lo que sí hace falta es que el comentario de la columna deje de mentir. Un `comment on column` que
-- enumera seis estados cuando hay ocho es peor que no tener comentario: quien abra la base a
-- diagnosticar algo va a creerle.

comment on column emision_de_guia.estado is
    'SOLICITADA, EN_CURSO, INDETERMINADA, EMITIDA, FALLIDA, PARCIAL, ANULADA o SIN_ANULAR. Los '
    'tres primeros cuentan como abierta y bloquean una emision nueva del mismo pedido: en los '
    'tres puede haber plata comprometida sin desenlace. ANULADA y SIN_ANULAR son el resultado de '
    'cancelar el pedido: en la primera las guias quedaron anuladas en la plataforma, y en la '
    'segunda al menos una pudo quedar viva y cobrable. INDETERMINADA, PARCIAL y SIN_ANULAR piden '
    'ojo humano y salen en la bandeja de revision.';

comment on column emision_de_guia.resuelta_en is
    'Cuando se cerro el intento, sea por desenlace de la plataforma o por la anulacion que sigue a '
    'cancelar el pedido.';
