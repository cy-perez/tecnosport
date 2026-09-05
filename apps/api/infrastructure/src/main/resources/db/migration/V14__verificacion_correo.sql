-- Registro de cliente y verificación de correo obligatoria (docs/08-seguridad-legal.md), Fase 4.

alter table usuario add column correo_verificado_en timestamptz;

-- Un solo uso, sin rotación ni familia (a diferencia de sesion_refresco): verificar el correo no
-- necesita esa cadena.
create table token_verificacion_correo (
    id uuid primary key,
    usuario_id uuid not null references usuario (id),
    creado_en timestamptz not null,
    expira_en timestamptz not null,
    usado_en timestamptz
);

create index ix_token_verificacion_correo_usuario on token_verificacion_correo (usuario_id);
