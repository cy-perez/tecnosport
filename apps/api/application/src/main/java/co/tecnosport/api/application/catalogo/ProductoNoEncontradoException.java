package co.tecnosport.api.application.catalogo;

import co.tecnosport.api.domain.compartido.Slug;

/**
 * No hay un producto publicado con ese slug. No distingue entre "no existe" y "existe pero está en
 * borrador": un visitante no debe poder averiguar cuál de los dos es el caso.
 */
public final class ProductoNoEncontradoException extends RuntimeException {

  public ProductoNoEncontradoException(Slug slug) {
    super("No existe un producto publicado con slug '" + slug.valor() + "'.");
  }
}
