-- El zip de cada ingesta visto como archivo, para la limpieza periodica del bucket privado
-- (10 de octubre de 2026).
--
-- El lote guarda la key del zip y nada mas: ni con que nombre se subio ni cuanto pesa, y un zip de
-- dos chats lo leen dos lotes. El historial lista archivos, no trabajos, y borrar uno se lleva solo
-- los bytes: el lote, sus mensajes y sus borradores se quedan.
create table archivo_ingesta (
    id uuid primary key,
    -- La misma key que `lote_ingesta.referencia_archivo`. Sin llave hacia alla porque no es unica
    -- en el lote (dos chats, dos lotes); el historial los une por esta columna.
    referencia text not null unique,
    -- `cascade`: el proveedor que se borra con su historial se lleva tambien esto.
    proveedor_id uuid not null references proveedor (id) on delete cascade,
    -- Lo que mando el navegador, sin carpetas. Solo para reconocerlo en la lista.
    nombre_original varchar(255),
    tamano_bytes bigint check (tamano_bytes is null or tamano_bytes >= 0),
    subido_en timestamptz not null,
    -- Nula mientras el zip sigue en el bucket.
    borrado_en timestamptz
);

-- La lista del panel, la mas reciente primero.
create index ix_archivo_ingesta_subido on archivo_ingesta (subido_en desc);

-- Los zips de antes: sin nombre ni tamano, que no se guardaban, con la fecha del primer lote que
-- los leyo. El id sale de `gen_random_uuid()` y no del dominio: es una fila que el dominio nunca
-- creo, y su id no ordena nada.
insert into archivo_ingesta (id, referencia, proveedor_id, subido_en)
select gen_random_uuid(), referencia_archivo, proveedor_id, min(creado_en)
  from lote_ingesta
 where referencia_archivo is not null
 group by referencia_archivo, proveedor_id;
