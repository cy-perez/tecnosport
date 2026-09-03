package co.tecnosport.api.application.pedido;

import co.tecnosport.api.domain.compartido.Dinero;
import java.util.UUID;

/**
 * {@code transportadora} y {@code guia} se validan en {@code Envio}, no aquí: no hace falta
 * duplicar la regla.
 */
public record DespacharPedidoComando(
    UUID pedidoId, String transportadora, String guia, Dinero costoEnvio, String actor) {}
