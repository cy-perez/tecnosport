-- La foto principal puede llevar un color (7 de octubre de 2026).
--
-- `V1` escribio el indice unico que garantiza una sola principal por producto, pero acotado a
-- `variante_id is null`:
--
--     create unique index ux_imagen_principal_por_producto
--         on imagen_producto (producto_id)
--         where tipo = 'PRINCIPAL' and variante_id is null;
--
-- Eso deja un hueco: un producto podria tener varias filas PRINCIPAL, con tal de que todas menos
-- una llevaran variante. Nunca paso porque `AprobarBorrador` forzaba el nulo en la principal -- y
-- al forzarlo descartaba el tono que quien revisa le habia marcado a esa foto.
--
-- Ahora ese tono importa: si la principal vale para todos los tonos se queda fuera de la galeria
-- de la ficha y solo encabeza la tarjeta del catalogo; si tiene un color, ademas abre la ficha.
-- Para distinguir los dos casos la principal tiene que poder llevar su variante.
--
-- El indice se ensancha a la condicion entera. **No es solo permitir el color: es que la
-- invariante queda mas fuerte que antes** -- una principal por producto, lleve variante o no. Es
-- seguro porque hoy no existe ninguna fila PRINCIPAL con variante: la unica via que las crea es
-- la aprobacion de un borrador, y hasta este cambio ponia nulo siempre.
drop index ux_imagen_principal_por_producto;

create unique index ux_imagen_principal_por_producto
    on imagen_producto (producto_id)
    where tipo = 'PRINCIPAL';
