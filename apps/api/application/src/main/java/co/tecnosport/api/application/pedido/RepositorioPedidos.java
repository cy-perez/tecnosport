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

  /**
   * Reclama el derecho a avisarle a un pedido que su plazo de entrega venció. Devuelve {@code true}
   * si lo ganó quien llama, {@code false} si ya estaba reclamado.
   *
   * <p><b>Es una sola escritura condicional y atómica</b>, no un guardado del agregado, y de eso
   * depende que nadie reciba dos veces el mismo correo. Dos razones, las dos reales:
   *
   * <p>La primera es que Cloud Run corre con un <b>mínimo</b> de una instancia, no con un máximo:
   * bajo carga hay varias, cada una con su tarea programada, y todas leen las mismas filas con el
   * aviso en nulo. Sin el {@code where} condicional, las N escriben y las N escriben un correo.
   *
   * <p>La segunda es el lote. Antes esto era {@code marcar} + {@code guardar} dentro de una sola
   * transacción para todo el barrido, y un fallo en el pedido veinte —o al comprometer— revertía
   * las marcas de los diecinueve anteriores <b>cuyos correos ya habían salido</b>. Con una
   * escritura por pedido, comprometida por sí misma, el peor caso es una marca puesta y un correo
   * que no salió: un aviso perdido, que es el lado por el que se prefiere fallar.
   */
  boolean reclamarAvisoDePlazo(UUID pedidoId, Instant ahora);
}
