package co.tecnosport.api.application.pedido;

import java.util.List;
import java.util.UUID;

/** Las guías se validan en {@code GuiaEnvio} y {@code Envio}, no aquí. */
public record DespacharPedidoComando(UUID pedidoId, List<GuiaDespachada> guias, String actor) {}
