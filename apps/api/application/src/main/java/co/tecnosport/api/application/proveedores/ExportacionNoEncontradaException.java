package co.tecnosport.api.application.proveedores;

/**
 * La key que mandó el panel no apunta a una exportación de ese proveedor, o el objeto no está en el
 * almacén. No se distingue entre las dos: un cliente que manda una key ajena no tiene por qué saber
 * si existe.
 */
public final class ExportacionNoEncontradaException extends RuntimeException {

  public ExportacionNoEncontradaException(String objectKey) {
    super("No hay una exportación subida en " + objectKey + ".");
  }
}
