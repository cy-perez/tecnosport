-- Las fotos de un borrador de tecnologia (ADR-0079): la skill de fichas las sube ya retocadas, en
-- el orden en que se publican, y aprobar las copia al producto. La primera es la principal.
create table borrador_tecnologia_foto (
    borrador_id uuid not null references borrador_tecnologia (id) on delete cascade,
    orden integer not null,
    foto_id uuid not null,
    -- Un ancho por linea: "ancho;bytes;clave". Las claves son del bucket privado del proveedor y
    -- no traen ni punto y coma ni salto de linea: las arma ClavesDeFotosDeTecnologia.
    variantes text not null,
    referencia_vista_previa varchar(300),
    alto integer not null,
    hash varchar(64) not null,
    alt_es varchar(300) not null,
    alt_en varchar(300) not null,
    subida_en timestamptz not null,
    -- Sin un unico por foto_id, por la misma razon que las configuraciones: Hibernate reescribe la
    -- coleccion por posicion, y quitar una foto del medio escribe un id en una posicion mientras
    -- sigue en otra. Que no se repita lo sostiene el agregado.
    primary key (borrador_id, orden)
);
