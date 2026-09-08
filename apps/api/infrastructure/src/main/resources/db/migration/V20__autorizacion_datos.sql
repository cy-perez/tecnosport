-- Constancia de autorización del tratamiento de datos personales (Fase 6).
-- Ley 1581 de 2012, docs/08-seguridad-legal.md.

-- Tabla propia y no columnas de usuario o de pedido: quien compra sin cuenta
-- también autoriza, y su constancia tiene que durar tanto como la de quien sí
-- la tiene. Se agrega y no se sobrescribe, igual que historial_pedido.
create table autorizacion_datos (
    id uuid primary key,
    -- Sin FK a usuario, por la misma razón que linea_carrito no la tiene a
    -- variante: convertiría un dato de auditoría en un error crudo de Postgres
    -- capaz de tumbar la compra que está intentando dejar constancia.
    usuario_id uuid,
    correo varchar(255) not null,
    -- La fija el servidor (POLITICA_DATOS_VERSION), así que acotarla es
    -- razonable: un valor absurdo aquí es una configuración mala y conviene que
    -- se note.
    version_politica varchar(40) not null,
    -- text y no varchar(45), aunque un IPv6 quepa en 45. La IP viene de una
    -- cabecera de proxy, o sea de fuera, y ya pasó una vez en este proyecto que
    -- una columna estrecha reventara en producción con un valor más largo del
    -- previsto (imagen_producto.hash, ADR-0019). Aquí el precio de equivocarse
    -- sería un 500 en mitad de una compra.
    direccion_ip text not null,
    origen varchar(20) not null,
    otorgada_en timestamptz not null
);

-- La consulta de auditoría es siempre "qué autorizó esta persona".
create index ix_autorizacion_datos_correo on autorizacion_datos (correo, otorgada_en desc);
