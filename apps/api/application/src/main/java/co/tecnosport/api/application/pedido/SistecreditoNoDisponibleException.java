package co.tecnosport.api.application.pedido;

/**
 * Sistecrédito lo ofrece el negocio, pero no para este pedido: hoy, porque el carrito no llega al
 * monto mínimo del crédito.
 *
 * <p>Separada de {@code MetodoDePagoNoHabilitadoException} por lo mismo que {@code
 * ContraentregaNoDisponibleException} lo está: una dice "para ningún pedido" y la otra "para este".
 * Al comprador se le cuentan cosas distintas —quitar el método del checkout, o decirle cuánto le
 * falta— y una sola excepción obligaría a adivinar cuál.
 */
public final class SistecreditoNoDisponibleException extends RuntimeException {

  public SistecreditoNoDisponibleException() {
    super("Sistecrédito no está disponible para este pedido.");
  }
}
