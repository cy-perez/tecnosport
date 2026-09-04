package co.tecnosport.api.application.pedido;

import java.util.UUID;

public record MarcarEntregadoComando(UUID pedidoId, String actor) {}
