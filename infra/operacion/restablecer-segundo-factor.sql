-- Restablece el segundo factor de un administrador (ADR-0096).
--
-- Para cuando no hay otra salida: perdió el teléfono **y** los códigos de respaldo, o se perdió o
-- se rotó sin migrar la llave SEGUNDO_FACTOR_LLAVE y ningún factor se puede descifrar. Borra el
-- factor y sus códigos de respaldo (en cascada), los desafíos pendientes de inicio de sesión y los
-- conteos del límite de intentos del segundo paso, y revoca todas sus sesiones. Con
-- ADMIN_EXIGE_SEGUNDO_FACTOR encendida, el siguiente inicio de sesión entra con la sesión que solo
-- sirve para activarlo otra vez; sin ella, entra con la clave.
--
-- **No cambia la clave.** Si el motivo es que alguien más pudo tener la clave, esto no basta:
-- recuperarla por correo después.
--
-- Cómo correrlo: reemplazar CORREO_DEL_ADMIN por el correo de la cuenta —una sola vez, en la
-- línea del select— y ejecutar el archivo entero en la consola SQL de la base (Neon en dev). Es un
-- solo bloque DO, y un bloque DO es atómico: o pasa todo o no pasa nada. Si el correo no es de un
-- ADMIN, o se dejó el marcador, el `into strict` falla y no se toca ninguna fila.
--
-- Lo ejecuta tal cual RestablecerSegundoFactorSqlTest, contra PostgreSQL de verdad.
do $$
declare
    objetivo uuid;
begin
    select id into strict objetivo
      from usuario
     where lower(correo) = lower('CORREO_DEL_ADMIN')
       and rol = 'ADMIN';

    delete from desafio_segundo_factor where usuario_id = objetivo;
    -- codigo_de_respaldo cae en cascada (V145).
    delete from segundo_factor where usuario_id = objetivo;
    delete from limite_intentos
     where clave = 'cuenta:segundo-factor:' || objetivo
        or clave like 'cuenta-ip:segundo-factor:' || objetivo || ':%';
    update sesion_refresco
       set revocado_en = now()
     where usuario_id = objetivo
       and revocado_en is null;

    raise notice 'Segundo factor restablecido para %', objetivo;
end
$$;
