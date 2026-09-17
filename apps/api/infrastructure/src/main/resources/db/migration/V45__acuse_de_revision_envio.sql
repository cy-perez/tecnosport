-- El acuse de revisión: alguien miró un envío quieto o una emisión con plata comprometida.
--
-- Existe para que la bandeja de revisión se pueda vaciar. Dos de los cinco estados que piden ojo
-- humano —CANCELADO y DESTRUIDO— son además terminales: de esas guías no llega otro evento nunca.
-- Una bandeja calculada solo a partir del estado las acumularía para siempre y a los pocos meses
-- sería una lista que nadie abre.
--
-- No resuelve nada, y es a propósito: acusar una emisión INDETERMINADA no la pasa a FALLIDA ni
-- desbloquea el pedido. Eso es plata y lo decide otro caso de uso. Aquí solo queda escrito quién
-- miró, cuándo y qué concluyó.

create table acuse_revision (
    id uuid primary key,
    tipo varchar(10) not null,
    -- Dos columnas y no una referencia suelta con un discriminador: así la llave foránea sigue
    -- existiendo. Un acuse que apunte a una guía que ya no está no es un dato, es basura que tapa
    -- una fila de la bandeja sin que nadie pueda ver contra qué.
    guia_id uuid references guia_envio (id),
    emision_id uuid references emision_de_guia (id),
    -- Nuestro reloj, no el de la transportadora. La regla que devuelve una guía a la bandeja
    -- compara esto contra evento_seguimiento.recibido_en —cuándo nos enteramos— y nunca contra
    -- ocurrio_en, que lo pone la transportadora: comparar dos relojes distintos haría que un evento
    -- con desfase pareciera anterior al acuse sin serlo, y el precio es una guía en excepción que
    -- desaparece de la vista sin que nadie la haya visto.
    revisado_en timestamptz not null,
    actor varchar(120) not null,
    nota text,
    constraint ck_acuse_revision_apunta_a_uno check (
        (tipo = 'GUIA' and guia_id is not null and emision_id is null)
        or (tipo = 'EMISION' and emision_id is not null and guia_id is null)
    )
);

-- Sin unicidad por referencia a propósito: una guía acusada que recibe un evento nuevo vuelve a la
-- bandeja y se acusa otra vez. Son dos filas y dos momentos, igual que evento_seguimiento y por el
-- mismo motivo: el día de la reclamación hay que poder decir quién sabía qué, y cuándo.
create index ix_acuse_revision_guia on acuse_revision (guia_id, revisado_en desc);
create index ix_acuse_revision_emision on acuse_revision (emision_id, revisado_en desc);

comment on table acuse_revision is
    'Alguien miro esto y dice que paso. Es lo que permite que la bandeja de revision se vacie: sin '
    'acuse, CANCELADO y DESTRUIDO se acumulan para siempre porque son terminales.';

comment on column acuse_revision.revisado_en is
    'Nuestro reloj. Se compara contra evento_seguimiento.recibido_en, nunca contra ocurrio_en, que '
    'lo pone la transportadora.';

comment on column acuse_revision.actor is
    'Quien miro. En la fila y no solo en el registro: esto decide sobre plata comprometida.';
