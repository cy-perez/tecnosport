-- ADR-0096. El segundo factor TOTP del panel: uno por usuario, con su id como llave.
--
-- secreto_cifrado es la clave TOTP cifrada con AES-256-GCM por la API (CifradorAesGcm), con el id
-- del usuario como dato asociado: 'v1:' + Base64(iv || cifrado). La base nunca la ve en claro, y una
-- fila copiada a otra cuenta no se descifra.
--
-- activado_en nulo es una activación pendiente: la clave ya se mostró, pero nadie ha demostrado
-- todavía que su aplicación genera el mismo código. ultimo_paso_usado es el último periodo de 30 s
-- cuyo código se aceptó, para no aceptar el mismo dos veces.
create table segundo_factor (
    usuario_id        uuid primary key references usuario (id) on delete cascade,
    secreto_cifrado   text        not null,
    creado_en         timestamptz not null,
    activado_en       timestamptz,
    ultimo_paso_usado bigint
);

-- Los diez códigos de respaldo, por su SHA-256 en hexadecimal (HuellaDeToken), como los tokens
-- desde V140. Borrar el factor borra sus códigos.
create table codigo_de_respaldo (
    huella     varchar(64) primary key,
    usuario_id uuid        not null references segundo_factor (usuario_id) on delete cascade,
    creado_en  timestamptz not null,
    usado_en   timestamptz
);

create index idx_codigo_de_respaldo_usuario on codigo_de_respaldo (usuario_id);
