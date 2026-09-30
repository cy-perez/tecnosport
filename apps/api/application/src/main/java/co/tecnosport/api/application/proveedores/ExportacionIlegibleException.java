package co.tecnosport.api.application.proveedores;

/**
 * El archivo no es una exportación de chat que se pueda leer: no es un zip, no trae ningún {@code
 * .txt}, o el texto no tiene ni una cabecera de mensaje. El motivo se escribe para el panel, que es
 * donde alguien va a decidir si exporta de nuevo.
 */
public final class ExportacionIlegibleException extends RuntimeException {

  public ExportacionIlegibleException(String motivo) {
    super(motivo);
  }

  public ExportacionIlegibleException(String motivo, Throwable causa) {
    super(motivo, causa);
  }
}
