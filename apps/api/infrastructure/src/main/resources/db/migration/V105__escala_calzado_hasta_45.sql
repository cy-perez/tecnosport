-- La escala de calzado llega hasta la 45 (11 de octubre de 2026).
--
-- V75 la dejo de la 34 a la 43, y el primer proveedor de calzado, Imperio Wicho, vende hasta la 45:
-- sus tenis de caballero van de la 40 a la 45. La escala es la que ordena las tallas en la ficha,
-- asi que la 44 y la 45 quedaban fuera de ella. Las tres ramas de calzado, como en V75.
update categoria set escala_tallas = E'34\n35\n36\n37\n38\n39\n40\n41\n42\n43\n44\n45'
where slug in ('calzado-dama', 'calzado-caballero', 'calzado-unisex');
