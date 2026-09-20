package co.tecnosport.api.application.catalogo;

/**
 * El nombre de la marca es único en todo el catálogo, sin distinguir mayúsculas.
 *
 * <p>Vive en {@code application} y no en el dominio por lo mismo que {@code SkuYaEnUsoException}:
 * la {@link co.tecnosport.api.domain.catalogo.Marca} que se está creando no puede ver el resto de
 * la tabla, así que "ya existe" es una pregunta que solo responde el repositorio.
 */
public final class MarcaYaExisteException extends RuntimeException {

  public MarcaYaExisteException(String nombre) {
    super("Ya existe una marca llamada '" + nombre + "'.");
  }
}
