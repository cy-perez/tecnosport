package co.tecnosport.api.application.pedido;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/** La alerta con el umbral con que se eligieron los pedidos, para que el panel diga el número. */
public record PedidosDetenidosEnPreparacion(Duration umbral, List<PedidoDetenido> pedidos) {

  public PedidosDetenidosEnPreparacion {
    Objects.requireNonNull(umbral, "El umbral no puede ser nulo.");
    pedidos = List.copyOf(Objects.requireNonNull(pedidos, "Los pedidos no pueden ser nulos."));
  }
}
