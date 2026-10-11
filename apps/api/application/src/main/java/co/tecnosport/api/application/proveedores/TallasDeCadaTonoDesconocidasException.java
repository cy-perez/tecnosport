package co.tecnosport.api.application.proveedores;

/**
 * Los dos borradores tienen tallas distintas y uno no dice sus tonos: unidos, no se sabría qué
 * talla tiene cada color, y se publicaría una que el proveedor no tiene. Hay que nombrar los tonos
 * de los dos antes de unirlos.
 */
public final class TallasDeCadaTonoDesconocidasException extends RuntimeException {

  public TallasDeCadaTonoDesconocidasException() {
    super("Las tallas no son las mismas y uno de los dos no dice sus tonos: nómbralos antes.");
  }
}
