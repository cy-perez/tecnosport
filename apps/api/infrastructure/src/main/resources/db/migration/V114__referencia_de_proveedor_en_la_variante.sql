-- El album de La Riverah es un solo producto desde el 13 de octubre de 2026, con una prenda por
-- cada SKU impreso en el pie de sus fotos. El SKU queda en las variantes de su prenda: con el, el
-- proveedor agota ese diseno sin agotar los demas ("AGOTADO polo RV102376").
--
-- Agotada no es INACTIVA: una variante inactiva no se hidrata en ninguna parte, y entonces un
-- pedido ya pagado de ella no podria emitir su guia, ni el guarda de ventas veria que se vendio al
-- borrar el producto. La agotada sigue activa y solo deja de venderse.
alter table variante add column referencia_proveedor varchar(40);
alter table variante add column agotada_por_proveedor boolean not null default false;

create index ix_variante_referencia_proveedor
    on variante (referencia_proveedor)
    where referencia_proveedor is not null;
