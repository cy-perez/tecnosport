package co.tecnosport.api.application.proveedores;

/**
 * Un tramo de ingesta falló. Lo que importa a quien lo llamó es si tiene sentido reintentarlo:
 *
 * <ul>
 *   <li><b>Sí</b>, si el fallo pudo ser pasajero —la base, el bucket, la red— y el lote volvió a la
 *       cola para que otro tramo lo retome. Con Cloud Tasks la tarea responde con error y la cola
 *       la reintenta.
 *   <li><b>No</b>, si el lote quedó cerrado: el fallo se repite siempre —un zip ilegible— o se
 *       acabaron los reintentos. Reintentar no cambiaría nada.
 * </ul>
 *
 * <p>La causa va dentro, con su traza, para el registro; el motivo que ve el panel ya quedó escrito
 * en el lote.
 */
public final class TramoFallidoException extends RuntimeException {

  private final boolean reintentar;

  public TramoFallidoException(Throwable causa, boolean reintentar) {
    super("El tramo de ingesta falló: " + causa, causa);
    this.reintentar = reintentar;
  }

  public boolean reintentar() {
    return reintentar;
  }
}
