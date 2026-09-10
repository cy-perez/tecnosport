package co.tecnosport.api.domain.retracto;

/**
 * Por dónde salió la plata. Los términos publicados prometen devolver "por el mismo medio de pago
 * que usaste o por el que acordemos contigo", así que el mismo medio no es el único posible y el
 * enum tiene que admitir los dos casos.
 *
 * <p>Ninguno de estos valores mueve dinero: son el registro de un acto que ocurrió por fuera del
 * sistema. Incluido {@code WOMPI} — hoy se anota a mano después de devolver desde el panel de la
 * pasarela.
 */
public enum MedioReembolso {
  WOMPI,
  TRANSFERENCIA_BANCARIA,
  EFECTIVO,
  OTRO
}
