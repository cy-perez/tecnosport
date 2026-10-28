-- Los nombres de las otras cuentas desde las que un proveedor publica en el mismo chat. Meraki
-- publica también como «Merakiapartados», y la ingesta, que solo reconocía el nombre principal,
-- ignoraba lo que llegaba desde esa cuenta (28 de octubre de 2026).
create table proveedor_otro_remitente (
    proveedor_id uuid not null references proveedor (id) on delete cascade,
    remitente varchar(120) not null,
    primary key (proveedor_id, remitente)
);
