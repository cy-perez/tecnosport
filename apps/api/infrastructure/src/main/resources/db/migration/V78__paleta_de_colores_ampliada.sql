-- La paleta de colores ampliada y en orden alfabetico (4 de octubre de 2026).
--
-- Los 24 de V75 dejaban productos sin su color: tonos de jean, neones de ropa deportiva, cueros de
-- bolso, pasteles. El HEX es el de referencia de cada nombre; lo que la muestra promete es el
-- nombre, y el HEX solo lo pinta.
--
-- Quedan fuera a proposito los que no son un color sino un diseno -multicolor, estampado, animal
-- print-: la muestra es un circulo de un solo HEX y pintaria uno que el producto no tiene.

insert into color_paleta (id, nombre, nombre_en, hex, orden) values
    (gen_random_uuid(), 'Aguamarina',      'Aquamarine',      '#7FFFD4', 0),
    (gen_random_uuid(), 'Amarillo neón',   'Neon yellow',     '#DFFF00', 0),
    (gen_random_uuid(), 'Arena',           'Sand',            '#C2B280', 0),
    (gen_random_uuid(), 'Azul claro',      'Light blue',      '#8FB8DE', 0),
    (gen_random_uuid(), 'Azul oscuro',     'Dark blue',       '#1C2E5A', 0),
    (gen_random_uuid(), 'Azul petróleo',   'Petrol blue',     '#1F4E5F', 0),
    (gen_random_uuid(), 'Azul rey',        'Royal blue',      '#4169E1', 0),
    (gen_random_uuid(), 'Berenjena',       'Eggplant',        '#614051', 0),
    (gen_random_uuid(), 'Bronce',          'Bronze',          '#CD7F32', 0),
    (gen_random_uuid(), 'Champaña',        'Champagne',       '#F7E7CE', 0),
    (gen_random_uuid(), 'Chocolate',       'Chocolate',       '#4E2A1E', 0),
    (gen_random_uuid(), 'Cobre',           'Copper',          '#B87333', 0),
    (gen_random_uuid(), 'Coñac',           'Cognac',          '#9A463D', 0),
    (gen_random_uuid(), 'Crema',           'Cream',           '#FFF5DC', 0),
    (gen_random_uuid(), 'Durazno',         'Peach',           '#FFCBA4', 0),
    (gen_random_uuid(), 'Gris claro',      'Light grey',      '#C8C8C8', 0),
    (gen_random_uuid(), 'Gris jaspe',      'Heather grey',    '#B5B5B5', 0),
    (gen_random_uuid(), 'Gris oscuro',     'Dark grey',       '#4A4A4A', 0),
    (gen_random_uuid(), 'Hueso',           'Off-white',       '#F2EBDD', 0),
    (gen_random_uuid(), 'Índigo',          'Indigo',          '#3F3A8C', 0),
    (gen_random_uuid(), 'Kaki',            'Khaki',           '#C3B091', 0),
    (gen_random_uuid(), 'Lavanda',         'Lavender',        '#B57EDC', 0),
    (gen_random_uuid(), 'Magenta',         'Magenta',         '#FF00FF', 0),
    (gen_random_uuid(), 'Marfil',          'Ivory',           '#FFFFF0', 0),
    (gen_random_uuid(), 'Mora',            'Blackberry',      '#5C2042', 0),
    (gen_random_uuid(), 'Naranja neón',    'Neon orange',     '#FF5F1F', 0),
    (gen_random_uuid(), 'Nude',            'Nude',            '#E3BC9A', 0),
    (gen_random_uuid(), 'Ocre',            'Ochre',           '#CC7722', 0),
    (gen_random_uuid(), 'Palo de rosa',    'Dusty rose',      '#D8A5A7', 0),
    (gen_random_uuid(), 'Rosado neón',     'Neon pink',       '#FF6EC7', 0),
    (gen_random_uuid(), 'Salmón',          'Salmon',          '#FA8072', 0),
    (gen_random_uuid(), 'Taupe',           'Taupe',           '#8B7D6B', 0),
    (gen_random_uuid(), 'Terracota',       'Terracotta',      '#C8664B', 0),
    (gen_random_uuid(), 'Turquesa',        'Turquoise',       '#40E0D0', 0),
    (gen_random_uuid(), 'Verde botella',   'Bottle green',    '#006A4E', 0),
    (gen_random_uuid(), 'Verde esmeralda', 'Emerald green',   '#009B77', 0),
    (gen_random_uuid(), 'Verde limón',     'Lime green',      '#9ACD32', 0),
    (gen_random_uuid(), 'Verde neón',      'Neon green',      '#39FF14', 0),
    (gen_random_uuid(), 'Verde oliva',     'Olive',           '#808000', 0)
on conflict do nothing;

-- El orden pasa a ser el alfabetico del nombre en espanol, sin tildes, para que cualquier cliente
-- que lea la paleta por `orden` -el panel, la app movil- la reciba ya ordenada. El panel en ingles
-- la reordena por el nombre en ingles.
update color_paleta c set orden = o.posicion
from (
    select id,
           row_number() over (
               order by translate(lower(nombre), 'áéíóúüñ', 'aeiouun'), nombre
           ) as posicion
    from color_paleta
) o
where c.id = o.id;
