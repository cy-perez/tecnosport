-- `imagen_producto.hash` vuelve a ser lo que docs/02-modelo-datos.md siempre dijo que era: el
-- SHA-256 del contenido, en hexadecimal. Hasta ahora el código guardaba ahí la key del objeto en
-- Cloud Storage (~99 caracteres), que ni siquiera cabía en varchar(80): la subida de un set de
-- rotación y la de la imagen principal fallaban con 500 contra el bucket real. Y como cada key es
-- única por construcción, la detección de recargas duplicadas que la columna promete nunca podría
-- haber funcionado.
--
-- Las filas que ya existen no tienen un hash de contenido y no hay forma de calcularlo: de las
-- imágenes sembradas no hay bytes (la URL apunta a un archivo que no existe), y de una imagen
-- subida de verdad habría que descargarla. Se deriva entonces del valor anterior, que era un
-- identificador estable y distinto para cada fila. Queda bien formado y sigue siendo único, pero
-- no es el hash del contenido: es el hash de su identificador previo.
update imagen_producto
set hash = encode(sha256(hash::bytea), 'hex')
where hash !~ '^[0-9a-f]{64}$';

-- Desde aquí en adelante el formato es parte del esquema, no solo una invariante del dominio: una
-- key de Cloud Storage vuelta a colar en esta columna revienta en el insert, no cuatro capas más
-- arriba.
alter table imagen_producto
  add constraint imagen_producto_hash_sha256 check (hash ~ '^[0-9a-f]{64}$');
