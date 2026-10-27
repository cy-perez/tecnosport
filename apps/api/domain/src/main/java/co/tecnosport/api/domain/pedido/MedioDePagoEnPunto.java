package co.tecnosport.api.domain.pedido;

/**
 * Cómo pagó en el punto quien recogió un pedido con {@link MetodoPago#PAGO_EN_PUNTO}. Son los dos
 * que el negocio recibe allí (26 de octubre de 2026, ADR-0094); queda en el historial de la
 * entrega.
 */
public enum MedioDePagoEnPunto {
  EFECTIVO,
  TRANSFERENCIA
}
