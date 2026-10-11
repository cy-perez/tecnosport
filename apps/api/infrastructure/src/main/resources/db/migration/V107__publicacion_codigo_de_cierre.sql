-- Por que una publicacion no dejo borrador, como codigo que el panel traduce (regla dura 4,
-- 11 de octubre de 2026). Lo mismo que V104 hizo con el lote.
--
-- Hasta hoy `motivo` guardaba la frase en espanol, y nadie la veia: el panel no listaba las
-- publicaciones de un lote. Desde ahora `codigo_motivo` dice el motivo (MotivoDePublicacion),
-- `dato_motivo` el valor que el texto interpola, y `motivo` se queda como el detalle interno para
-- diagnosticar -- el mensaje de la API que rechazo una extraccion --, que el panel no muestra.
alter table publicacion_proveedor add column codigo_motivo varchar(40);
alter table publicacion_proveedor add column dato_motivo varchar(200);

-- La restriccion de V70 pedia `motivo` en las descartadas y fallidas. Se quita antes de llenar los
-- codigos: llenarlos deja en nulo el `motivo` de las descartadas, que ya no tiene nada que decir.
-- La nueva, al final, pide el codigo.
alter table publicacion_proveedor drop constraint ck_publicacion_proveedor_motivo;

-- Las de antes: la frase de cada motivo conocido pasa a su codigo. Las frases son las que
-- escribia ResolverBorrador hasta hoy. Varias frases juntas (varios productos descartados) toman
-- el codigo de la primera que reconocen, en el orden de estas sentencias.
update publicacion_proveedor set codigo_motivo = 'VIENE_EN_CHAT_DE_CABALLERO', motivo = null
 where codigo_motivo is null and motivo like 'El anuncio viene en el chat de caballero%';
update publicacion_proveedor set codigo_motivo = 'SIN_PRODUCTO', motivo = null
 where codigo_motivo is null and motivo like 'El extractor no reconoció un producto%';
update publicacion_proveedor set codigo_motivo = 'AGOTADO_FUERA_DE_CATALOGO', motivo = null
 where codigo_motivo is null and motivo like 'Anuncia como agotado%';
update publicacion_proveedor
   set codigo_motivo = 'MISMA_REFERENCIA_EN_REVISION',
       dato_motivo = substring(motivo from 'sus ([0-9]+) fotos se sumaron'),
       motivo = null
 where codigo_motivo is null and motivo like 'Es la misma referencia de un borrador%';
update publicacion_proveedor set codigo_motivo = 'ANUNCIO_REPETIDO', motivo = null
 where codigo_motivo is null and motivo like 'Es el mismo anuncio, con la misma foto%';

-- Las fallidas guardan el detalle tal cual: es lo que dijo la API, y sirve para diagnosticar.
update publicacion_proveedor set codigo_motivo = 'ERROR_AL_RESOLVER',
       dato_motivo = substring(motivo from '^Error inesperado \((.*)\)\.$')
 where codigo_motivo is null and estado = 'ERROR' and motivo like 'Error inesperado (%';
update publicacion_proveedor set codigo_motivo = 'EXTRACCION_FALLIDA'
 where codigo_motivo is null and estado = 'ERROR'
   and (motivo like 'La API %' or motivo like 'El extractor %' or motivo like 'El lector de fotos %');
-- El resto de las fallidas las tumbo una regla del dominio al resolverlas: ExcepcionDeDominio y
-- ExportacionIlegibleException guardaban su mensaje tal cual, sin el "Error inesperado".
update publicacion_proveedor set codigo_motivo = 'ERROR_AL_RESOLVER'
 where codigo_motivo is null and estado = 'ERROR';

update publicacion_proveedor set codigo_motivo = 'DESCONOCIDO'
 where codigo_motivo is null and estado = 'DESCARTADA';

alter table publicacion_proveedor add constraint ck_publicacion_proveedor_motivo check (
    estado not in ('DESCARTADA', 'ERROR') or codigo_motivo is not null
);
