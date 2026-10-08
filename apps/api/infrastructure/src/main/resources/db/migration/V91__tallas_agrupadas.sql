-- Las tallas agrupadas de ropa (8 de octubre de 2026).
--
-- Los conjuntos deportivos y varias prendas de dama no tallan S o M sino S-M: una prenda que sirve
-- a las dos. El negocio pidio ofrecerlas en Dama y en Caballero, intercaladas en la escala, para que
-- la revision de un borrador y el alta de una variante las den a marcar en su sitio. La ficha no se
-- llena de tachadas: solo ensena las tallas que el producto trae (`soloTallasElegibles`).
--
-- Solo donde la escala sigue siendo la que puso V75: si alguien la cambio a mano en una base, esa
-- decision no se pisa.
update categoria
set escala_tallas = E'XS\nXS-S\nS\nS-M\nM\nM-L\nL\nL-XL\nXL\nXL-XXL\nXXL\nXXL-XXXL\nXXXL'
where slug in ('ropa-dama', 'ropa-caballero')
  and escala_tallas = E'XS\nS\nM\nL\nXL\nXXL\nXXXL';
