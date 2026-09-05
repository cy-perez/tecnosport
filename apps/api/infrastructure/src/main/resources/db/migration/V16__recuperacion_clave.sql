-- Recuperación de contraseña, token de un solo uso, 30 minutos (docs/08-seguridad-legal.md), Fase 4.
-- Mismo patrón que token_verificacion_correo (V14), separado a propósito: uno confirma un buzón,
-- este cambia la clave.

create table token_recuperacion_clave (
    id uuid primary key,
    usuario_id uuid not null references usuario (id),
    creado_en timestamptz not null,
    expira_en timestamptz not null,
    usado_en timestamptz
);

create index ix_token_recuperacion_clave_usuario on token_recuperacion_clave (usuario_id);
