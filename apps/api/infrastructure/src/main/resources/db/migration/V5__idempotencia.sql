-- Idempotencia por Idempotency-Key (Fase 3). Ver docs/03-api.md.

-- estado_http, tipo_contenido y cuerpo quedan nulos mientras estado = 'PENDIENTE':
-- la llave se reclama antes de ejecutar el efecto de negocio, para que un
-- proceso que muere entre comprometer ese efecto y guardar la respuesta no
-- deje la puerta abierta a un reintento que lo repita.
create table idempotencia (
    llave varchar(100) primary key,
    metodo varchar(10) not null,
    ruta varchar(200) not null,
    estado varchar(20) not null,
    estado_http integer,
    tipo_contenido varchar(100),
    cuerpo text,
    creado_en timestamptz not null default now(),
    completado_en timestamptz
);
