-- Meraki vende polos de horma reducida ("pedir una talla mas"), Imperio Wicho tenis que hay que
-- "aumentar 2 tallas", y anuncia otros en tallas EUR, que son la nacional mas una (13 de octubre de
-- 2026, ADR-0085). La tienda vende la talla que le queda a quien compra; al proveedor hay que
-- pedirle la suya.
--
-- Se guarda cuantas tallas mas arriba las nombra el proveedor, no la talla ya calculada: la de
-- cada variante sale de su talla de hoy cuando se consulta el pedido, asi que cambiarla en el panel
-- o agregar un color despues de aprobar no deja una talla del proveedor vieja ni ausente. El
-- borrador lo trae de la ingesta y la aprobacion lo copia al producto.
alter table borrador_producto add column ajuste_de_talla integer not null default 0;
alter table borrador_producto add constraint ck_borrador_producto_ajuste_de_talla
    check (ajuste_de_talla between -4 and 4);

alter table producto add column ajuste_de_talla integer not null default 0;
alter table producto add constraint ck_producto_ajuste_de_talla
    check (ajuste_de_talla between -4 and 4);
