package co.tecnosport.api.application.proveedores;

/**
 * Se pidió reintentar las fallidas de un lote mientras otro del mismo proveedor está en la cola o a
 * medio procesar. Las ingestas de un proveedor corren en el orden en que se subieron —el chat de
 * caballero antes que el general—, y un lote viejo que vuelve a la cola en medio las desordenaría.
 */
public final class OtraIngestaDelProveedorEnCursoException extends RuntimeException {

  public OtraIngestaDelProveedorEnCursoException() {
    super("El proveedor tiene otra ingesta en curso. Reintenta las fallidas cuando termine.");
  }
}
