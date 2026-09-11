package co.tecnosport.api.application.pedido;

import co.tecnosport.api.domain.pedido.EstadoPedido;
import co.tecnosport.api.domain.pedido.NumeroPedido;
import co.tecnosport.api.domain.pedido.Pedido;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RepositorioPedidos {

  Optional<Pedido> buscarPorId(UUID id);

  void guardar(Pedido pedido);

  /**
   * Reserva de forma atómica el siguiente secuencial del número legible para {@code anio}
   * (apps/api/CLAUDE.md: {@code TS-2026-000123}). Cada año arranca su propio contador en 1.
   */
  NumeroPedido siguienteNumero(int anio);

  /**
   * Paginación por página, no por cursor (docs/03-api.md) — la vista de operación mínima del panel,
   * no el catálogo. {@code estado} nulo lista todos, más recientes primero; filtrado por estado
   * ordena por más antiguo primero (recaudo pendiente: lo más urgente arriba).
   */
  PedidosPaginados buscarTodosPaginado(int pagina, int tamanoPagina, EstadoPedido estado);

  /**
   * Historial de rechazos en la entrega (docs/11-pagos-y-envios.md: "si un correo... ya rechazó
   * pedidos en la entrega, no se le ofrece más"). Sin teléfono en el dominio todavía, solo por
   * correo. {@code RECHAZADO_EN_ENTREGA} es terminal (ver {@code EstadoPedido}), así que basta con
   * el estado actual del pedido, sin recorrer su historial.
   */
  boolean tieneRechazoEnEntrega(String correo);

  /**
   * Los pedidos que el vigilante del plazo de entrega tiene que mirar: en alguno de {@code
   * estados}, sin aviso previo y nacidos antes de {@code creadosAntesDe}.
   *
   * <p>El filtro es <b>grueso</b> a propósito y no decide nada: quién incumplió lo dice {@code
   * PlazoDeEntrega} sobre la fecha del historial, que es donde vive la regla. {@code creadoEn}
   * siempre es anterior o igual al inicio del plazo —un pedido no se paga antes de existir—, así
   * que acotar por él nunca se salta a nadie, solo evita traerse el catálogo entero de pedidos
   * recientes.
   *
   * <p>Sin paginar: lo que queda después de los tres filtros son los pedidos vivos que llevan más
   * de un mes sin entregarse y a los que todavía no se les ha escrito. Si esa lista llega a ser
   * grande, el problema no es la consulta.
   */
  List<Pedido> buscarSinAvisoDePlazo(Collection<EstadoPedido> estados, Instant creadosAntesDe);
}
