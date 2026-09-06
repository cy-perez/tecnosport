package co.tecnosport.api.application.catalogo;

/**
 * El objeto no existe todavía en el almacén de imágenes — la subida nunca llegó, o llegó a otra
 * key.
 */
public final class ObjetoDeImagenNoEncontradoException extends RuntimeException {

  public ObjetoDeImagenNoEncontradoException(String objectKey) {
    super("No existe el objeto de imagen '" + objectKey + "'.");
  }
}
