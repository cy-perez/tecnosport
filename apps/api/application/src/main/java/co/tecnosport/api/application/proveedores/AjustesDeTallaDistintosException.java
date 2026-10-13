package co.tecnosport.api.application.proveedores;

/**
 * Los dos borradores salen del mismo anuncio pero el proveedor nombra sus tallas de distinta
 * manera: el de dama y el de caballero de un anuncio unisex (ADR-0085). Unidos, una talla no diría
 * cuál pedirle.
 */
public final class AjustesDeTallaDistintosException extends RuntimeException {

  public AjustesDeTallaDistintosException() {
    super(
        "El proveedor nombra las tallas de los dos de distinta manera: unidos, no se sabría qué"
            + " talla pedirle por cada una.");
  }
}
