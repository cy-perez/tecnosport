-- Los proveedores locales (26 de octubre de 2026).
--
-- El negocio decidio volver a ofrecer la recogida en el punto de Medellin, pero solo para lo que
-- surten los proveedores de la ciudad: un pedido se recoge si todo lo que lleva viene de uno
-- local. Hoy son La Riverah y Violeta. Se buscan por nombre porque el id no es el mismo en
-- desarrollo que en la nube, y si no existen no pasa nada: se marcan desde el panel.
alter table proveedor add column es_local boolean not null default false;

-- Las dos son de ropa y bolsos: un proveedor de tecnologia con un nombre parecido no se marca.
update proveedor set es_local = true
where (lower(nombre) like '%riverah%' or lower(nombre) like '%violeta%')
  and linea <> 'TECNOLOGIA';
