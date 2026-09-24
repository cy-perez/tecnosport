package co.tecnosport.api.domain.catalogo;

/**
 * Las líneas de negocio (docs/00-producto.md). Es el nivel <b>más grueso</b> del catálogo: el menú
 * del sitio pinta una rama por cada una y el filtro principal un control, y de aquí cuelgan las
 * {@link Categoria}, que son filas en base de datos y afinan dentro de la línea.
 *
 * <p><b>{@code CELULARES} se llamó así hasta el 14 de septiembre de 2026</b>, cuando el negocio
 * amplió lo tecnológico a relojes, audífonos, cargadores, cables, power banks, consolas, parlantes,
 * computadores, tablets y proyectores. Ninguno de esos es un celular, y colgarlos de una línea con
 * ese nombre habría sido una mentira de modelo que se paga en cada consulta y en cada URL. Son
 * <b>categorías</b> dentro de {@code TECNOLOGIA}, que es para lo que existe {@link Categoria}.
 *
 * <p>Y esa decisión ya se cobró dos veces. El 24 de septiembre de 2026 el negocio dejó de vender
 * cables, cargadores y power banks, y {@code V62} lo resolvió con un {@code delete} de tres filas.
 * Ese mismo día entró el árbol de categorías completo —treinta filas, hasta dos niveles bajo la
 * línea— y no hizo falta tocar este enum para meter "Blusas", "Licras" ni "Manos libres".
 *
 * <p><b>Por qué son cuatro y no tres.</b> {@code ROPA_Y_CALZADO} se partió en {@code ROPA} y {@code
 * CALZADO} el 24 de septiembre de 2026, y es un cambio de línea de verdad, no una categoría
 * disfrazada: son dos surtidos que no comparten ni talla, ni proveedor, ni criterio de empaque
 * —{@code ContenidoDeclarado} tenía que declarar "Ropa y calzado deportivo" en la guía de una
 * camiseta sola— y el comprador que busca tenis no está mirando camisas. Que costara tocar el enum,
 * las traducciones, las pruebas y el filtro es exactamente la señal de que era un cambio de nivel
 * grueso.
 *
 * <p>La regla se mantiene igual de dura para lo que venga: <b>una línea nueva es un cambio de
 * código, de traducciones, de pruebas y del filtro que ve el comprador; una categoría nueva es una
 * fila, y desde el árbol se crea desde el panel sin desplegar nada</b>. Si algo se puede modelar
 * como fila, no se modela como valor de enum.
 */
public enum LineaCatalogo {
  ROPA,
  CALZADO,
  BOLSOS,
  TECNOLOGIA
}
