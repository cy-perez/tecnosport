package co.tecnosport.api.presentation.pedido.dto;

/**
 * Sin monto, a propósito: lo calcula el servidor —lo que el pedido cobró menos lo que ya se
 * devolvió—. {@code medio} solo hace falta cuando el dinero había entrado; si faltaba y hacía
 * falta, el caso de uso lo rechaza.
 */
public record RecibirPedidoRechazadoRequest(String medio, String comprobante) {}
