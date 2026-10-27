-- Los pagos de Wompi que quedaron PENDIENTE sin id de transacción y sin webhook no los concilia
-- nadie: la API de Wompi consulta por el id, no por la referencia. El panel los enseña pasado el
-- umbral (12 h por omisión) para que alguien los busque a mano en el portal de Wompi; esta tabla
-- guarda quién los revisó, para que dejen de salir. Tabla aparte y no una columna de `pago`: es una
-- anotación de operación, no estado del pago, y el adaptador reescribe la fila entera del pago en
-- cada guardado.
create table revision_pago_sin_transaccion (
    pago_id uuid primary key references pago (id) on delete cascade,
    revisado_por varchar(255) not null,
    revisado_en timestamptz not null
);
