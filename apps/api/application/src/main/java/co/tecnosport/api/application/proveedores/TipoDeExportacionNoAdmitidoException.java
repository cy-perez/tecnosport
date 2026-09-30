package co.tecnosport.api.application.proveedores;

/** Solo se sube un zip: es lo único que WhatsApp exporta con las fotos dentro. */
public final class TipoDeExportacionNoAdmitidoException extends RuntimeException {

  public TipoDeExportacionNoAdmitidoException(String contentType) {
    super("Una exportación de chat es un archivo zip, no " + contentType + ".");
  }
}
