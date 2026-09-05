package co.tecnosport.api.presentation.pedido.dto;

import java.time.Instant;

/**
 * Un registro por cada transición del pedido (docs/02-modelo-datos.md: {@code HistorialPedido}).
 */
public record HistorialPedidoRespuesta(String estado, Instant fecha, String actor, String motivo) {}
