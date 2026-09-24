-- Salen del catalogo las tres categorias que ninguna lista de proveedor puede llenar
-- (24 de septiembre de 2026).
--
-- `V38` sembro once categorias de tecnologia. Tres de ellas nunca tuvieron de donde salir:
--
--   * Cables de cargador -- la skill `listas-de-proveedor` los descarta desde el 14/09/2026,
--     el MISMO dia en que esta categoria se creo: la lista solo trae los extremos
--     ("TIPO C - LIGHTNING") y sin longitud, potencia ni marca no se publica un cable.
--   * Cargadores y Power banks -- descartados el 24/09/2026 por la misma regla llevada hasta
--     el final: lo que se vende junto al equipo entra, lo que alimenta al equipo no. Se venden
--     solos, con margen bajo y rotacion lenta, y cada referencia obliga a investigar un precio
--     de mercado propio para muy poca venta.
--
-- La linea TECNOLOGIA queda con ocho: celulares, tablets, relojes, audifonos, consolas,
-- computadores, proyectores y parlantes -- exactamente las ocho de `CATEGORIAS_INCLUIDAS` en
-- `.claude/skills/listas-de-proveedor/scripts/parsear_lista.py`. Que sean las mismas no es
-- coincidencia y no se sostiene solo: lo afirma `CategoriasDeTecnologiaTest`.
--
-- **Por que un borrado y no un `activa = false`.** No hay columna de estado en `categoria`, y
-- agregarla para tres filas que nunca se usaron seria modelar una excepcion como si fuera una
-- regla. El endpoint publico ya oculta las categorias vacias (`listarConProductosPublicados`),
-- asi que estas tres no las ve ningun comprador; las ve el panel, que es donde estorban:
-- ofrecer "Cargadores" al cargar un producto invita a crear el que la lista no puede reponer.
--
-- **Sin `where not exists`.** Si alguna tuviera un producto detras, la llave foranea de
-- `producto.categoria_id` (`V1__esquema_inicial.sql`) hace fallar esta migracion, y eso es lo
-- correcto: que alguien decida que hacer con ese producto. Un borrado que se salta en silencio
-- deja una categoria viva y a nadie enterado. Al escribir esto las tres estaban vacias.
delete from categoria
where slug in ('cargadores', 'power-banks', 'cables-de-cargador');

-- Y entra la octava, que llevaba diez dias sin existir fuera de desarrollo.
--
-- `V38` renombro la linea con `update categoria set linea = 'TECNOLOGIA' where linea = 'CELULARES'`
-- dando por hecho que la fila 'Celulares' ya estaba. No lo estaba: la unica que la crea es
-- `SembradorCatalogo`, y ese solo corre con la tabla de productos vacia. O sea que las otras siete
-- categorias son dato real por migracion y esta era siembra de desarrollo, justo la distincion que
-- `V38` se tomo el trabajo de explicar en su propio comentario -- "el dato real que toda instalacion
-- necesita es una migracion; el ejemplo para poder desarrollar es una siembra".
--
-- En una base que ya tenia productos, ese update no actualizo ninguna fila y nadie se entero,
-- porque nada comparaba la lista del catalogo con la de la skill. Lo destapo
-- `CategoriasDeTecnologiaTest` al primer intento: sobre un contenedor limpio salian siete.
insert into categoria (id, nombre, slug, linea, creado_en) values
    (gen_random_uuid(), 'Celulares', 'celulares', 'TECNOLOGIA', now())
on conflict (slug) do nothing;
