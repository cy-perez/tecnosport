package co.tecnosport.api.application.proveedores;

/**
 * El borrador que se quería unir a otro tiene fotos subidas desde el panel: son archivos suyos y se
 * irían con él. Hay que subirlas en el que se queda antes de unirlos. Con nombre propio para que el
 * panel diga eso y no «una regla del negocio».
 */
public final class BorradorConFotosSubidasException extends RuntimeException {

  public BorradorConFotosSubidasException() {
    super("El otro borrador tiene fotos subidas desde el panel: súbelas en este antes de unirlos.");
  }
}
