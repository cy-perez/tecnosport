package co.tecnosport.api.application.catalogo;

/**
 * Hay productos colgando de esta categoría, así que no se puede borrar ni convertir en rama.
 *
 * <p>Las dos cosas se rechazan por el mismo motivo y por eso comparten excepción: <b>un producto
 * cuelga siempre de una hoja</b>. Si "Camisas" tiene productos y alguien le crea "Manga larga"
 * debajo, esos productos quedan en un nodo intermedio, y entonces "ver todo lo de Camisas" y "ver
 * lo que cuelga directamente de Camisas" dejan de ser la misma consulta — para el comprador, lo
 * segundo no significa nada.
 *
 * <p>La salida es la misma en los dos casos: mover los productos primero.
 */
public final class CategoriaConProductosException extends RuntimeException {

  public CategoriaConProductosException(String nombre, String queSeIntentaba) {
    super(
        "La categoría '"
            + nombre
            + "' tiene productos: "
            + queSeIntentaba
            + ". Mueve primero sus productos a otra categoría.");
  }
}
