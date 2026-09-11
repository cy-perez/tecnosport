-- Cuándo se le avisó al comprador que el plazo de entrega venció.
--
-- Los términos publicados prometen treinta (30) días calendario para entregar (Ley 1480 de 2011,
-- art. 18) y que, si no se cumple, el comprador puede terminar el contrato y recuperar su dinero.
-- Hasta ahora nadie vigilaba ese vencimiento: las dos únicas tareas programadas eran la purga de
-- carritos y la conciliación de Wompi, así que un pedido pagado y sin despachar lo incumplía en
-- silencio.
--
-- El vencimiento en sí NO se guarda: se calcula con PlazoDeEntrega sobre la fecha que ya está en
-- historial_pedido, por el mismo motivo por el que la fecha de entrega tampoco es una columna
-- (docs/02-modelo-datos.md). Lo que esta columna anota es un hecho nuevo que no se deduce de
-- ningún estado: que se escribió el correo, y cuándo. De ella depende que el vigilante no vuelva a
-- escribirle al comprador en cada vuelta.
--
-- Nula en todos los pedidos anteriores, y así se queda: avisar hoy de un plazo vencido hace meses
-- sería mandar un correo que ya no ayuda a nadie. Solo el filtro por creado_en de la tarea decide
-- a quién alcanza.
alter table pedido add column aviso_plazo_entrega_enviado_en timestamptz;

-- La tarea busca exactamente esto: pedidos vivos, sin aviso, nacidos antes del corte. El índice es
-- parcial porque la fila deja de interesarle apenas se le avisa, y así no crece con el histórico.
create index ix_pedido_sin_aviso_de_plazo on pedido (creado_en)
    where aviso_plazo_entrega_enviado_en is null;

comment on column pedido.aviso_plazo_entrega_enviado_en is
    'Cuando se le aviso al comprador que vencieron los 30 dias calendario para entregar '
    '(Ley 1480 de 2011, art. 18). El vencimiento se calcula del historial, no se guarda: esto solo '
    'anota que el correo salio, para no repetirlo. Nula si aun no se le ha avisado.';
