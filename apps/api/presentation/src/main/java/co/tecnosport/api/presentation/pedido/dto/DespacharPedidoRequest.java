package co.tecnosport.api.presentation.pedido.dto;

/** {@code transportadora} y {@code guia} se validan en {@code Envio}, no aquí. */
public record DespacharPedidoRequest(String transportadora, String guia, long costoEnvio) {}
