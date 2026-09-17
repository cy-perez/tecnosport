-- De qué ya se avisó, para que el vigilante de la bandeja no repita el mismo correo cada vuelta.
--
-- Separada de `acuse_revision` a propósito, aunque las dos digan "esto ya se atendió": el acuse es
-- una persona afirmando que miró, y saca la fila de la bandeja. Un aviso es el sistema diciendo que
-- avisó, y **no** la saca: lo que está quieto sigue quieto hasta que alguien lo mire. Guardarlos en
-- la misma tabla haría que avisar contara como revisar, que es justo al revés de lo que hace falta.

create table aviso_revision (
    tipo varchar(10) not null,
    referencia uuid not null,
    -- Del último aviso, no del primero. Es lo que permite volver a avisar cuando llega una novedad
    -- posterior: se compara contra el mismo instante que el acuse —cuándo nos enteramos— y por eso
    -- la regla de "esto es nuevo" es una sola en todo el sistema.
    avisado_en timestamptz not null,
    primary key (tipo, referencia)
);

comment on table aviso_revision is
    'De que ya se aviso. No es un acuse: avisar no cuenta como revisar, y lo que esta quieto sigue '
    'quieto hasta que una persona lo mire.';

comment on column aviso_revision.avisado_en is
    'Del ultimo aviso. Una novedad posterior a esta fecha vuelve a armar el aviso, con el mismo '
    'criterio que usa el acuse para devolver una guia a la bandeja.';
