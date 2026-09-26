-- El buzon de sugerencias (26 de septiembre de 2026).
--
-- Tabla propia y no una fila de `solicitud_atencion`: aquella registra peticiones, quejas,
-- reclamos y derechos de datos personales, o sea cosas con un reloj legal corriendo, y por eso
-- tiene radicado, plazo, prorroga, respuesta y un `radicada_por` que es siempre una persona del
-- negocio. Una sugerencia no tiene nada de eso. Meterla alli habria obligado a inventarle un plazo
-- que la ley no le pone y un responsable que no existe.
--
-- Se guarda ademas de avisar por correo, y no en vez de: el aviso pasa por la bandeja de salida,
-- que reintenta pero puede acabar rindiendose, y una sugerencia perdida no se recupera de ninguna
-- parte.
create table sugerencia (
    id uuid primary key,
    -- `text` y no un `varchar(2000)`: el tope de dos mil caracteres es una regla de dominio
    -- (`Sugerencia.MAXIMO_CARACTERES_MENSAJE`) y ahi es donde tiene que fallar, con un mensaje que
    -- quien escribe pueda entender. Duplicarlo aqui solo cambiaria ese error por un 500 crudo de
    -- Postgres el dia que las dos cifras dejen de coincidir -- que es lo que le paso a
    -- `imagen_producto.hash` (ADR-0019).
    mensaje text not null,
    -- Opcional a proposito: sin correo esta fila no tiene un solo dato personal, y esa es la forma
    -- honesta de pedir una opinion. Cuando si lo hay, su constancia de autorizacion vive en
    -- `autorizacion_datos` con origen SUGERENCIA, escrita en la misma transaccion.
    correo varchar(255),
    recibida_en timestamptz not null
);

-- La unica consulta previsible es "que ha llegado ultimamente", cuando exista la bandeja que las
-- liste. Un indice sobre la fecha cuesta poco y es el que esa pantalla va a pedir.
create index ix_sugerencia_recibida_en on sugerencia (recibida_en desc);
