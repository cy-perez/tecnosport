-- Autenticación, solo ADMIN por ahora (Fase 3). Ver docs/08-seguridad-legal.md.
-- Sin registro de cliente ni verificación de correo todavía: eso es Fase 4.

create table usuario (
    id uuid primary key,
    correo varchar(255) not null unique,
    clave_hash varchar(255) not null,
    rol varchar(20) not null,
    creado_en timestamptz not null
);

-- Un registro por cada eslabón de la rotación del token de refresco
-- (docs/08-seguridad-legal.md): 30 días, con detección de reutilización.
-- familia_id agrupa la cadena de un mismo login.
create table sesion_refresco (
    id uuid primary key,
    usuario_id uuid not null references usuario (id),
    familia_id uuid not null,
    creado_en timestamptz not null,
    expira_en timestamptz not null,
    usado_en timestamptz,
    revocado_en timestamptz
);

create index ix_sesion_refresco_usuario on sesion_refresco (usuario_id);

-- Revocar una familia entera (detección de reutilización) filtra por estas dos
-- columnas juntas.
create index ix_sesion_refresco_familia on sesion_refresco (familia_id, revocado_en);
