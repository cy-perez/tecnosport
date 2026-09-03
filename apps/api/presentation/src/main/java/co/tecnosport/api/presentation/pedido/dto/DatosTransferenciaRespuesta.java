package co.tecnosport.api.presentation.pedido.dto;

/**
 * Solo va en la respuesta cuando {@code metodoPago == TRANSFERENCIA_MANUAL}
 * (docs/11-pagos-y-envios.md). {@code referencia} es el número legible del pedido: el mismo que ya
 * identifica el pedido en todo lo demás, no una referencia aparte que inventar.
 */
public record DatosTransferenciaRespuesta(
    String banco, String tipoCuenta, String numeroCuenta, String titular, String referencia) {}
