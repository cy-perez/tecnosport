package co.tecnosport.api.application.catalogo;

/**
 * Una categoría no puede colgar de sí misma ni de una de sus descendientes.
 *
 * <p>Con el tope de dos niveles, la única forma de armar un ciclo es colgar una categoría de su
 * propia hija, y aun así se comprueba explícitamente: el día que el tope suba, el ciclo deja de ser
 * imposible por accidente, y un árbol con un ciclo no se recorre — se cuelga.
 */
public final class CicloDeCategoriasException extends RuntimeException {

  public CicloDeCategoriasException(String nombre) {
    super("La categoría '" + nombre + "' no puede colgar de sí misma ni de una de sus hijas.");
  }
}
