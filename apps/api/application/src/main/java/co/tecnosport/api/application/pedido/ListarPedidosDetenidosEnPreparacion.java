package co.tecnosport.api.application.pedido;

import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.domain.pedido.EstadoPedido;
import co.tecnosport.api.domain.pedido.HistorialPedido;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Los pedidos que llevan más del umbral en {@code EN_PREPARACION} (3 días por omisión, {@code
 * tecnosport.alertas.en-preparacion-dias}). Solo alerta: no vence nada ni libera nada.
 *
 * <p>El caso que la motivó: un pedido de recogida o de contraentrega que llega a preparación
 * conserva su reserva sin vencimiento —{@code CrearPedido} la crea sin vigencia— y ninguno de los
 * vigilantes lo mira: {@code VencerRecogidasSinRetirar} empieza en {@code LISTO_PARA_RECOGER} y
 * {@code VencerContraentregaSinVerificar} en {@code CONFIRMADO_CONTRAENTREGA}. Si nadie lo despacha
 * ni lo cancela, la unidad queda apartada para siempre. Se enseñan todos los que llevan ahí más del
 * umbral y no solo esos dos: un pedido pagado detenido tres días en preparación también es algo que
 * alguien tiene que mirar, por el plazo de entrega prometido.
 *
 * <p>El tiempo cuenta desde la <b>última</b> llegada al estado según el historial, no desde la
 * creación del pedido: una contraentrega verificada ayer no lleva tres días en preparación aunque
 * se haya creado hace una semana.
 */
public final class ListarPedidosDetenidosEnPreparacion {

  /**
   * Tope de la consulta. Una alerta con más que esto ya no es una lista para mirar uno por uno, y
   * el problema es otro.
   */
  static final int MAXIMO = 200;

  private final RepositorioPedidos repositorioPedidos;
  private final Reloj reloj;
  private final Duration umbral;

  public ListarPedidosDetenidosEnPreparacion(
      RepositorioPedidos repositorioPedidos, Reloj reloj, Duration umbral) {
    this.repositorioPedidos = Objects.requireNonNull(repositorioPedidos);
    this.reloj = Objects.requireNonNull(reloj);
    this.umbral = Objects.requireNonNull(umbral, "El umbral no puede ser nulo.");
    if (umbral.isNegative() || umbral.isZero()) {
      throw new IllegalArgumentException("El umbral de la alerta debe ser positivo.");
    }
  }

  public PedidosDetenidosEnPreparacion ejecutar() {
    Instant corte = reloj.ahora().minus(umbral);
    return new PedidosDetenidosEnPreparacion(
        umbral,
        repositorioPedidos
            .buscarIdsEnEstadoDesdeAntesDe(EstadoPedido.EN_PREPARACION, corte, MAXIMO)
            .stream()
            .flatMap(id -> repositorioPedidos.buscarPorId(id).stream())
            // Releído: entre la consulta y aquí pudo salir del estado.
            .filter(pedido -> pedido.estado() == EstadoPedido.EN_PREPARACION)
            .flatMap(
                pedido ->
                    llegada(pedido.historial()).stream()
                        .map(desde -> new PedidoDetenido(pedido, desde)))
            .toList());
  }

  private static Optional<Instant> llegada(java.util.List<HistorialPedido> historial) {
    return historial.stream()
        .filter(h -> h.estado() == EstadoPedido.EN_PREPARACION)
        .map(HistorialPedido::fecha)
        .reduce((primera, ultima) -> ultima);
  }
}
