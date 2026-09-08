-- Última actividad del carrito, para poder purgar los inactivos (Fase 6).
-- docs/02-modelo-datos.md dice que un carrito vive 30 días, pero hasta ahora no
-- había ni columna ni tarea: un carrito anónimo se quedaba en la base para
-- siempre.

-- Se expira por actividad y no por creado_en: un carrito que alguien sigue
-- usando cada semana lleva meses creado y no es basura, y borrarlo sería
-- borrarle la compra en curso a un cliente vivo.
alter table carrito add column actualizado_en timestamptz;

-- Los carritos que ya existen no tienen historia de actividad; lo más cercano
-- que se sabe de ellos es cuándo nacieron.
update carrito set actualizado_en = creado_en where actualizado_en is null;

alter table carrito alter column actualizado_en set not null;

-- La purga barre por este campo.
create index ix_carrito_actualizado_en on carrito (actualizado_en);
