-- Para quien es el tenis de un borrador (ADR-0089, 25 de octubre de 2026).
--
-- Lo dice la foto en las tandas de Tenis Al Pie, y el anuncio en el de dama y el de caballero que
-- parte un anuncio unisex (ADR-0085). Decide la rama del calzado que el panel propone al aprobar:
-- Dama, Caballero o Unisex. Nulo en lo que no es calzado, en lo que no se sabe y en los borradores
-- de antes.
alter table borrador_producto add column genero_de_calzado varchar(12);
alter table borrador_producto add constraint ck_borrador_producto_genero_de_calzado
    check (genero_de_calzado in ('DAMA', 'CABALLERO', 'UNISEX'));
