-- ADR-0096. El segundo paso pendiente de un inicio de sesión: la clave ya fue correcta y falta el
-- código. Un solo uso y cinco minutos de vida. Como los tokens desde V140, lo que viaja al cliente
-- es el UUID y lo que se guarda es su SHA-256 en hexadecimal (HuellaDeToken).
create table desafio_segundo_factor (
    huella     varchar(64) primary key,
    usuario_id uuid        not null references usuario (id) on delete cascade,
    creado_en  timestamptz not null,
    expira_en  timestamptz not null,
    usado_en   timestamptz
);

create index idx_desafio_segundo_factor_usuario on desafio_segundo_factor (usuario_id);
