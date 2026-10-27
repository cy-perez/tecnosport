package co.tecnosport.api.application.pedido;

import co.tecnosport.api.domain.pedido.MedioDePagoEnPunto;
import java.util.UUID;

/**
 * {@code numeroPresentado} es el que mostró quien vino a recoger, tal como lo dictó o lo enseñó.
 * {@code medioDePago} solo va cuando el pedido se paga en el punto, y entonces es obligatorio.
 */
public record EntregarEnPuntoComando(
    UUID pedidoId, String numeroPresentado, MedioDePagoEnPunto medioDePago, String actor) {}
