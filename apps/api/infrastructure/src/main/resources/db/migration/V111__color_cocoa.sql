-- Cocoa entra a la paleta (12 de octubre de 2026).
--
-- Violeta lo escribe en sus anuncios -"Talla ML(cocoa)", "cocoa, vino, azul, negro"- y la paleta no
-- lo tenia: la propuesta de prendas dejaba ese tono sin color, y quien aprobaba elegia a ojo entre
-- Cafe, Chocolate y Rosado. El HEX es el cafe cocoa de referencia; lo que la muestra promete es el
-- nombre.

insert into color_paleta (id, nombre, nombre_en, hex, orden) values
    (gen_random_uuid(), 'Cocoa', 'Cocoa', '#875F42', 0)
on conflict do nothing;

-- El mismo orden alfabetico de V78 y V80, con el nuevo dentro.
update color_paleta c set orden = o.posicion
from (
    select id,
           row_number() over (
               order by translate(lower(nombre), 'áéíóúüñ', 'aeiouun'), nombre
           ) as posicion
    from color_paleta
) o
where c.id = o.id;
