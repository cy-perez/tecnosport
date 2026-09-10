package co.tecnosport.api.presentation.pedido.dto;

import java.math.BigDecimal;

/**
 * {@code monto} y {@code medio} solo hacen falta cuando el dinero ya habia entrado. Si falta y
 * hacia falta, el caso de uso lo rechaza: un pedido cancelado sin reintegro despues de haber
 * cobrado es plata retenida sin explicacion.
 */
public record CancelarPedidoRequest(
    String motivo, BigDecimal monto, String medio, String comprobante) {}
