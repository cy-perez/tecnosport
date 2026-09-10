package co.tecnosport.api.domain.garantia;

/**
 * Las tres salidas que la ley concede, y las tres tienen que existir en el sistema.
 *
 * <p>Un sistema que solo represente {@code REINTEGRO} —que es lo que pasa cuando la garantía se
 * modela como "devolver el dinero"— empuja a devolver plata donde bastaba reparar, y deja sin
 * rastro las dos salidas más frecuentes. Los términos publicados prometen las tres: "tienes derecho
 * a que lo reparemos, lo repongamos o te devolvamos el dinero, según el caso".
 */
public enum DesenlaceGarantia {
  REPARACION,
  REPOSICION,
  REINTEGRO
}
