package co.tecnosport.api.domain.reintegro;

/**
 * Por dónde salió la plata. Los términos publicados prometen devolver "por el mismo medio de pago
 * que usaste o por el que acordemos contigo", así que el mismo medio no es el único posible y el
 * enum tiene que admitir los dos casos.
 *
 * <p>Ninguno de estos valores mueve dinero: son el registro de un acto que ocurrió por fuera del
 * sistema. Incluido {@code WOMPI} — hoy se anota a mano después de devolver desde el panel de la
 * pasarela.
 *
 * <p><b>{@code SISTECREDITO} no significa lo mismo que los demás</b>, y conviene no confundirlo
 * ({@code adr/0048}). En los otros cuatro el dinero vuelve al comprador. Aquí el comprador nunca
 * pagó: quedó debiéndole un crédito a Sistecrédito, así que lo que se deshace no es una
 * transferencia sino <b>el crédito y el pagaré</b>, y lo pide el comercio desde el portal Credinet.
 * Si nadie lo pide, esa persona sigue pagando cuotas de algo que devolvió. No hay API para esto:
 * es una solicitud que hace una persona.
 */
public enum MedioReintegro {
  WOMPI,
  SISTECREDITO,
  TRANSFERENCIA_BANCARIA,
  EFECTIVO,
  OTRO
}
