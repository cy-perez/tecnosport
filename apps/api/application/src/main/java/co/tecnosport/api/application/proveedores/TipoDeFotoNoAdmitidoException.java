package co.tecnosport.api.application.proveedores;

/**
 * Una foto subida a un borrador es JPEG o PNG: es lo que la aprobación sabe abrir para medirla
 * ({@link ProcesadorDeImagenes}). Un WebP o un AVIF pasaría la subida y fallaría al aprobar, con el
 * formulario ya lleno.
 */
public final class TipoDeFotoNoAdmitidoException extends RuntimeException {

  public TipoDeFotoNoAdmitidoException(String contentType) {
    super("La foto de un borrador es JPEG o PNG, no " + contentType + ".");
  }
}
