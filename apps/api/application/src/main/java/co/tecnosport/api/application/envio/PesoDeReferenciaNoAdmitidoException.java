package co.tecnosport.api.application.envio;

/**
 * Esa categoría no lleva peso de referencia ({@code adr/0071}), por uno de dos motivos:
 *
 * <ul>
 *   <li><strong>Es de una línea que no se promedia</strong>, la tecnología: sus productos se miden
 *       con la ficha del fabricante, uno por uno.
 *   <li><strong>No es una hoja</strong>: un producto solo cuelga de una hoja, así que un peso
 *       puesto en "Ropa › Dama" no lo leería nadie, y quien lo puso creería que cubrió todas las
 *       prendas de Dama.
 * </ul>
 */
public final class PesoDeReferenciaNoAdmitidoException extends RuntimeException {

  private PesoDeReferenciaNoAdmitidoException(String mensaje) {
    super(mensaje);
  }

  public static PesoDeReferenciaNoAdmitidoException porLaLinea(String nombre) {
    return new PesoDeReferenciaNoAdmitidoException(
        "La categoría '"
            + nombre
            + "' es de una línea que no se cotiza con promedios: sus productos se miden uno por"
            + " uno.");
  }

  public static PesoDeReferenciaNoAdmitidoException porqueTieneHijas(String nombre) {
    return new PesoDeReferenciaNoAdmitidoException(
        "La categoría '"
            + nombre
            + "' tiene subcategorías: el peso se pone en cada una de ellas, que es de donde"
            + " cuelgan los productos.");
  }
}
