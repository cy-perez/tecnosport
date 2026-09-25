package co.tecnosport.api.application.catalogo;

/**
 * No se borra lo que está en la vitrina. Hay que retirarlo primero.
 *
 * <p>Dos pasos y no uno, a propósito. Retirar es reversible y su efecto se ve de inmediato: el
 * producto desaparece de la rejilla y su enlace responde 404. Borrar no tiene vuelta. Obligar a
 * pasar por el primero deja a quien lo hace un rato con el resultado a la vista antes de tomar la
 * decisión que no se puede deshacer, y es lo que separa un borrado de un descuido.
 */
public final class ProductoPublicadoException extends RuntimeException {

  public ProductoPublicadoException(String nombre) {
    super(
        "El producto '"
            + nombre
            + "' está publicado: no se puede borrar. Retíralo de la vitrina primero.");
  }
}
