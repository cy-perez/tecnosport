-- ADR-0074: una cuenta puede entrar con Google. La que nace así no tiene clave hasta que la pida por
-- recuperación, así que clave_hash deja de ser obligatoria; lo que sigue siendo obligatorio es tener
-- al menos una forma de entrar.
--
-- google_sub es el identificador estable de la cuenta de Google (el claim `sub` del ID token). El
-- correo de una cuenta de Google puede cambiar; el sub no. Único: una cuenta de Google entra a una
-- sola cuenta de la tienda.
alter table usuario alter column clave_hash drop not null;

alter table usuario add column google_sub varchar(255);

alter table usuario
    add constraint uq_usuario_google_sub unique (google_sub);

alter table usuario
    add constraint ck_usuario_alguna_forma_de_entrar
        check (clave_hash is not null or google_sub is not null);
