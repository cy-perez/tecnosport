package co.tecnosport.api.application.catalogo;

/**
 * No se borra una rama con hojas debajo.
 *
 * <p>Borrar en cascada habría sido una línea menos de código y la forma más rápida de perder trece
 * categorías por un clic: desde el panel, "Dama" y "Camisas" se ven igual de borrables. Que el
 * borrado obligue a vaciar la rama primero hace visible cuánto se está tirando.
 */
public final class CategoriaConHijasException extends RuntimeException {

  public CategoriaConHijasException(String nombre, int cuantas) {
    super(
        "La categoría '"
            + nombre
            + "' tiene "
            + cuantas
            + " subcategoría(s): bórralas o muévelas antes de borrarla.");
  }
}
