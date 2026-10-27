package co.tecnosport.api.application.pedido;

import java.util.UUID;

public record MarcarListoParaRecogerComando(UUID pedidoId, String actor) {}
