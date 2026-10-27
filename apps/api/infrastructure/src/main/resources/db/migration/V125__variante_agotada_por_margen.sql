-- Revision adversarial del 27 de octubre de 2026: un costo nuevo del proveedor podia dejar el margen
-- sobre el precio de contado por debajo del minimo (10 %, regla 18 de la skill de listas), y la
-- variante se seguia reponiendo y vendiendo. Desde hoy queda agotada por el proveedor
-- (agotada_por_proveedor, V114), y esta columna dice que fue por el margen: es la que la devuelve a
-- la venta cuando el margen vuelve -y no a una prenda que el proveedor agoto por su SKU- y la que
-- lee el aviso del panel.
alter table variante add column agotada_por_margen boolean not null default false;

create index ix_variante_agotada_por_margen
    on variante (producto_id)
    where agotada_por_margen;
