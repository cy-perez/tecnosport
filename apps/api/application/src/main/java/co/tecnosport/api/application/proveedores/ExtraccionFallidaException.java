package co.tecnosport.api.application.proveedores;

/**
 * El extractor no pudo: la API no respondió tras los reintentos, devolvió algo que no cabe en el
 * esquema, o se negó. La publicación queda en {@code ERROR} con este motivo y el resto del lote
 * sigue.
 */
public final class ExtraccionFallidaException extends RuntimeException {

  public ExtraccionFallidaException(String motivo) {
    super(motivo);
  }

  public ExtraccionFallidaException(String motivo, Throwable causa) {
    super(motivo, causa);
  }
}
