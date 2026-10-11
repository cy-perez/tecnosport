-- Por que fallo un lote, como codigo que el panel traduce (regla dura 4, 11 de octubre de 2026).
--
-- Hasta hoy detalle_error guardaba la frase en espanol y el panel la pintaba tal cual, tambien en
-- ingles. Desde ahora codigo_error dice el motivo (MotivoDeFalloDeLote) y detalle_error guarda solo
-- el dato que el texto interpola: la fecha que no se pudo leer, el codigo del ultimo fallo.
alter table lote_ingesta add column codigo_error varchar(40);

-- El check de V69 pedia detalle_error en todo lote en error. Con codigos, muchos fallos no llevan
-- dato: lo que se exige ahora es el codigo (abajo, despues de llenarlo). La parte del terminado se
-- queda como estaba.
alter table lote_ingesta drop constraint ck_lote_ingesta_cerrado;
alter table lote_ingesta add constraint ck_lote_ingesta_cerrado
    check (estado <> 'TERMINADO' or resumen_mensajes_leidos is not null);

-- Los lotes en error de antes: la frase de cada motivo conocido pasa a su codigo, y el dato que
-- llevaba dentro, a detalle_error. Las frases son las que escribia el codigo hasta hoy.
update lote_ingesta set codigo_error = 'REINICIO', detalle_error = null
 where estado = 'ERROR' and detalle_error like 'La aplicación se reinició%';
update lote_ingesta set codigo_error = 'COLA_LLENA', detalle_error = null
 where estado = 'ERROR' and detalle_error like 'La cola de ingestas estaba llena%';
update lote_ingesta set codigo_error = 'COLA_NO_DISPONIBLE', detalle_error = null
 where estado = 'ERROR' and detalle_error like 'No pudimos poner la ingesta en la cola%';
update lote_ingesta set codigo_error = 'SIN_REINTENTOS', detalle_error = null
 where estado = 'ERROR' and detalle_error like 'El procesamiento se interrumpió%';
update lote_ingesta set codigo_error = 'ZIP_SIN_CHAT', detalle_error = null
 where estado = 'ERROR' and detalle_error like 'El zip no trae ningún archivo .txt%';
update lote_ingesta set codigo_error = 'ZIP_INVALIDO', detalle_error = null
 where estado = 'ERROR' and detalle_error like 'El archivo no es un zip%';
update lote_ingesta set codigo_error = 'ZIP_DEMASIADO_GRANDE', detalle_error = null
 where estado = 'ERROR' and detalle_error like 'El zip descomprimido pasa del tope%';
update lote_ingesta set codigo_error = 'NO_ES_UN_CHAT', detalle_error = null
 where estado = 'ERROR' and detalle_error like 'El archivo de texto no tiene ni un mensaje%';
update lote_ingesta set codigo_error = 'EXPORTACION_NO_ENCONTRADA', detalle_error = null
 where estado = 'ERROR' and detalle_error like 'La exportación ya no está en el almacén%';
update lote_ingesta set codigo_error = 'ESTRUCTURA_NO_ACORDADA', detalle_error = null
 where estado = 'ERROR' and detalle_error like 'La estructura del archivo no es la acordada%';
update lote_ingesta set codigo_error = 'SIN_ARCHIVO', detalle_error = null
 where estado = 'ERROR' and detalle_error like 'El lote no tiene archivo que leer%';
update lote_ingesta
   set codigo_error = 'FECHA_ILEGIBLE',
       detalle_error = substring(detalle_error from 'no se pudo leer: (.*)$')
 where estado = 'ERROR' and detalle_error like 'Una fecha de la exportación no se pudo leer:%';
update lote_ingesta
   set codigo_error = 'ERROR_INESPERADO',
       detalle_error = substring(detalle_error from '^Error inesperado \((.*)\)\.$')
 where estado = 'ERROR' and detalle_error like 'Error inesperado (%';

-- Lo que no se reconocio: el codigo lo dice, y la frase vieja se queda en la columna sin mostrarse.
update lote_ingesta set codigo_error = 'DESCONOCIDO'
 where estado = 'ERROR' and codigo_error is null;

alter table lote_ingesta add constraint ck_lote_ingesta_error_con_codigo
    check (estado <> 'ERROR' or codigo_error is not null);
