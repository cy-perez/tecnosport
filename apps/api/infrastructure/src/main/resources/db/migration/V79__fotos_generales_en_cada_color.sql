-- Si las fotos generales acompanan a cada color en la ficha (4 de octubre de 2026).
--
-- Una foto sin tono vale para todos los colores: al elegir uno, la ficha ensena las de ese tono y
-- detras las generales. Hay productos que solo traen fotos por color, y ahi la general -casi
-- siempre la principal- mezcla en la galeria de un color la foto de otro. Esto lo apaga por
-- producto. `true` por omision: todo lo publicado sigue viendose como hasta hoy.
alter table producto add column fotos_generales_en_cada_color boolean not null default true;
