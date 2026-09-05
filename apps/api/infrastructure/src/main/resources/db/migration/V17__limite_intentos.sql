-- Límite de intentos por IP y por cuenta (docs/08-seguridad-legal.md), Fase 4.
-- Contador de ventana fija: una fila por llave ("ip:..." o "cuenta:...."), sin bloqueo pesimista
-- a propósito (ver LimitadorDeIntentos) — no hay dinero ni existencia en juego aquí.

create table limite_intentos (
    clave varchar(300) primary key,
    contador integer not null,
    ventana_expira_en timestamptz not null
);
