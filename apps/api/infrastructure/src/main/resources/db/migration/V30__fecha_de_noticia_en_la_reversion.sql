-- La columna se llama ahora por lo que de verdad guarda.
--
-- V26 la creo como `fecha_del_hecho`, y el javadoc del agregado decia otra cosa: "cuando el comprador
-- tuvo noticia de lo ocurrido, no cuando compro ni cuando escribio". El panel tambien: la etiqueta
-- decia "Fecha del hecho" y el texto de ayuda, justo debajo, "cuando el comprador tuvo noticia de lo
-- ocurrido". Dos operadores leyendo lo mismo escriben dos fechas distintas en la misma columna, y de
-- ella cuelga el plazo de cinco dias habiles del articulo 51 — con lo cual el panel podia marcar
-- vencida una solicitud que estaba en plazo.
--
-- El nombre correcto es el de la noticia, porque es lo que promete la clausula publicada y lo que el
-- Decreto 587 de 2016 usa para la operacion fraudulenta o no solicitada. Para las otras dos causales
-- el decreto cuenta desde que el producto debio recibirse o se recibio defectuoso; el sitio promete
-- una sola regla, la de la noticia, que es mas amplia a favor del consumidor y por eso admisible.
-- Queda dicho en el agregado.
--
-- Renombrar y no agregar: no hay dos datos, hay uno mal nombrado. Un `add column` habria dejado la
-- vieja llena de valores que nadie sabria como leer.
alter table solicitud_reversion
    rename column fecha_del_hecho to fecha_de_noticia;

comment on column solicitud_reversion.fecha_de_noticia is
    'Cuando el comprador tuvo noticia del hecho, que es desde donde corren sus cinco dias habiles '
    'para solicitar la reversion (art. 51 de la Ley 1480 y Decreto 587 de 2016). No es cuando ocurrio '
    'el hecho ni cuando radico: de un fraude uno se entera despues.';
