-- Los tipos de prenda que no se le compran a un proveedor (12 de octubre de 2026).
--
-- A Violeta no se le compran pantalones, sudaderas ni shorts, y el anuncio que trae uno se
-- descarta entero aunque venga con una blusa. El tipo es el nombre de `TipoProductoProveedor`; que
-- no sea `OTRO` lo defiende el dominio, no una restriccion aqui (ADR-0019). Todos los proveedores
-- de antes quedan sin exclusiones: se les sigue comprando todo lo que anuncian.
create table proveedor_tipo_excluido (
    proveedor_id uuid not null references proveedor (id) on delete cascade,
    tipo varchar(40) not null,
    primary key (proveedor_id, tipo)
);
