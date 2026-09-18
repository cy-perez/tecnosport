-- De qué cobro extra ya se avisó, para que el vigilante no repita el mismo correo cada vuelta.
--
-- Tabla propia y no una fila más en `aviso_revision`, aunque las dos digan "de esto ya se avisó":
-- aquélla apunta a algo nuestro con un UUID —una guía, una emisión— y se vuelve a ganar cuando llega
-- una novedad posterior. Un cobro extra no es nuestro, no tiene UUID de la plataforma y no tiene
-- novedades: cuando cambia lo que importa, cambia la clave.

create table aviso_sobrecosto (
    -- Compuesta, y no un identificador del proveedor, porque la respuesta de
    -- `GET /api/v1/finance/extra-charges` no trae ninguno: verificado el 18 de septiembre de 2026
    -- contra el esquema del endpoint, que declara `shipment_id` y `package_id` —los dos del envío—
    -- y nada del cargo. La compone `SobrecostoDeEnvio.clave()` con el envío, el tipo, el monto y la
    -- fecha de detección; el monto entra a propósito, así que una reliquidación por otra cifra
    -- vuelve a avisar en vez de pasar callada.
    clave varchar(300) primary key,
    avisado_en timestamptz not null
);

comment on table aviso_sobrecosto is
    'De que cobro extra de la transportadora ya se aviso. Una fila por cobro, y no se borra: es la '
    'unica memoria de que ese sobrecosto se conocio.';

comment on column aviso_sobrecosto.clave is
    'Compuesta por SobrecostoDeEnvio.clave(): el endpoint no devuelve ningun identificador del '
    'cargo. Lleva el monto, asi que una reliquidacion por otra cifra se avisa de nuevo.';
