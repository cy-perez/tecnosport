-- Los tokens de sesión de refresco, de recuperación de clave y de verificación de correo dejan de
-- guardarse en claro. Hasta aquí el token era la llave primaria tal cual: una copia de la base
-- entregaba sesiones de 30 días y enlaces de cambio de clave listos para usar. Desde aquí la llave
-- es su SHA-256 en hexadecimal (HuellaDeToken, en el dominio) y se busca por ella.
--
-- Las filas que ya existían se convierten con la misma expresión que calcula Java —el texto del
-- UUID en minúsculas y con guiones, en UTF-8—, así que las sesiones vivas no se cierran y los
-- enlaces ya enviados siguen sirviendo. sha256(bytea) existe desde PostgreSQL 11.

alter table sesion_refresco
    alter column id type varchar(64) using encode(sha256(convert_to(id::text, 'UTF8')), 'hex');
alter table sesion_refresco rename column id to huella;

alter table token_recuperacion_clave
    alter column id type varchar(64) using encode(sha256(convert_to(id::text, 'UTF8')), 'hex');
alter table token_recuperacion_clave rename column id to huella;

alter table token_verificacion_correo
    alter column id type varchar(64) using encode(sha256(convert_to(id::text, 'UTF8')), 'hex');
alter table token_verificacion_correo rename column id to huella;
