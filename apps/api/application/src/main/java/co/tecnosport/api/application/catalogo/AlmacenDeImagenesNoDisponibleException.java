package co.tecnosport.api.application.catalogo;

/**
 * El almacén de imágenes no respondió o rechazó la operación. Existe para que el error del SDK de
 * Cloud Storage no cruce hasta la presentación y salga como un 500 genérico: es un tercero caído, y
 * se responde como tal.
 */
public class AlmacenDeImagenesNoDisponibleException extends RuntimeException {

  public AlmacenDeImagenesNoDisponibleException(Throwable causa) {
    super("El almacén de imágenes no está disponible en este momento.", causa);
  }
}
