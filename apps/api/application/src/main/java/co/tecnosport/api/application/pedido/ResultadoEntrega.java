package co.tecnosport.api.application.pedido;

import co.tecnosport.api.domain.pedido.Pedido;

/**
 * El pedido entregado y si su inventario quedó confirmado, para quien tenga con qué registrarlo.
 *
 * <p>La entrega no se deshace porque el inventario no cuadre: la mercancía ya está en manos del
 * comprador y negarle la transición al pedido no la trae de vuelta. Mismo criterio que {@code
 * ResultadoEventoDePago.APLICADO_SIN_CONFIRMAR_INVENTARIO} con un pago aprobado tarde (ADR-0014):
 * el hecho se registra igual y la discrepancia se señala para revisión manual.
 *
 * <p>{@code inventarioConfirmado} es siempre cierto en un pedido pagado en línea o por
 * transferencia: esos confirman su reserva cuando entra el dinero, mucho antes de entregar.
 */
public record ResultadoEntrega(Pedido pedido, boolean inventarioConfirmado) {}
