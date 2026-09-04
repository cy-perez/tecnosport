package co.tecnosport.api.application.pedido;

import co.tecnosport.api.domain.compartido.Dinero;
import java.util.UUID;

public record ConciliarRecaudoComando(UUID pedidoId, Dinero comisionRecaudo, String actor) {}
