-- El calzado que ya estaba publicado toma el titulo de un tenis (ADR-0089, 25 de octubre de 2026).
--
-- Desde hoy todo el calzado se titula "Tenis importados estilo <Marca> <Modelo>", sin el genero,
-- que queda en la categoria. Lo nuevo lo titula la ingesta (TituloDeTenis); esto pone igual lo que
-- ya estaba: "Tenis estilo Nike para dama" es "Tenis importados estilo Nike", y "Tenis para
-- caballero tipo media" es "Tenis importados". Lo que sigue a "estilo" es la marca y el modelo, hasta
-- el genero, "importado" o la talla, como en TituloDeTenis.desde.
--
-- Solo los productos de la linea de calzado cuyo nombre empieza por "Tenis" y que no tienen ya la
-- forma nueva. El slug no cambia: es la direccion publicada del producto.
with calzado as (
  -- En dos pasos, porque Postgres decide si una expresion es perezosa por la expresion entera y
  -- no por cada cuantificador: lo que sigue a "estilo", y de eso lo que va antes del primer corte.
  select p.id,
         regexp_replace(
           substring(p.nombre from '(?i)estilo\s+(.+)$'),
           '\s+(para\s|de\s+(dama|caballero|hombre|mujer)|unisex|importad[oa]s?|talla).*$|\s*[,;(].*$',
           '',
           'i'
         ) as nombre_del_tenis
  from producto p
  join categoria c on c.id = p.categoria_id
  where c.linea = 'CALZADO'
    and p.nombre ~* '^tenis(\s|$)'
    and p.nombre !~* '^tenis importados(\s|$)'
)
update producto p
set nombre = case
               when nullif(btrim(calzado.nombre_del_tenis), '') is null then 'Tenis importados'
               else 'Tenis importados estilo ' || btrim(calzado.nombre_del_tenis)
             end
from calzado
where calzado.id = p.id;
