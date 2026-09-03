package co.tecnosport.api.application.pedido;

import java.util.UUID;

public record ConciliarTransferenciaComando(UUID pedidoId, String actor) {}
