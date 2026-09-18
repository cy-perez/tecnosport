-- El comprobante de la compra, que se le manda por correo a quien compró en cuanto el pedido queda
-- en firme. Esta columna es la marca de "ya se mandó", y existe por lo mismo que
-- aviso_plazo_entrega_enviado_en: bajo carga hay varias instancias corriendo la misma tarea, y sin
-- un reclamo condicional las N leen la misma fila en nulo y las N mandan el correo.
--
-- Nullable a propósito y sin relleno hacia atrás: un pedido anterior a esta migración nunca recibió
-- comprobante, así que decir lo contrario sería mentir en una tabla. Lo que sí pasa es que la tarea
-- se los mandará al arrancar, y eso es correcto: quien compró y no recibió nada tiene derecho a su
-- soporte aunque llegue tarde. Hoy no hay ningún pedido real, así que ni siquiera se nota.
alter table pedido
    add column comprobante_enviado_en timestamptz;

comment on column pedido.comprobante_enviado_en is
    'Cuándo se mandó el comprobante de compra. Lo escribe solo el reclamo condicional de la tarea, nunca el agregado.';

-- Índice parcial: la tarea busca exactamente las filas con la marca en nulo y el pedido en firme, y
-- esas son pocas y siempre recientes. Un índice sobre la columna entera crecería con todo el
-- histórico para responder una consulta que nunca mira las filas ya mandadas.
create index idx_pedido_sin_comprobante
    on pedido (estado)
    where comprobante_enviado_en is null;
