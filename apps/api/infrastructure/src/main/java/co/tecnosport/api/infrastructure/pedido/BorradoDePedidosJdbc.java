package co.tecnosport.api.infrastructure.pedido;

import co.tecnosport.api.application.pedido.BorradoDePedidos;
import co.tecnosport.api.application.pedido.PedidoNoEliminableException;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * SQL directo porque mira tablas de cinco módulos que el agregado {@code Pedido} no conoce. La
 * lista de tablas es la de las llaves foráneas hacia {@code pedido}: si una migración nueva agrega
 * otra, el borrado fallará con una violación de llave en vez de borrar de más, y aquí hay que
 * decidir a qué compromiso pertenece.
 */
@Repository
public class BorradoDePedidosJdbc implements BorradoDePedidos {

  private static final String HAY_PAGO_EN_JUEGO =
      "select exists (select 1 from pago where pedido_id = :id"
          + " and estado in ('APROBADO', 'PENDIENTE'))";

  private static final String HAY_ENVIO =
      "select exists (select 1 from envio where pedido_id = :id)"
          + " or exists (select 1 from emision_de_guia where pedido_id = :id)";

  private static final String HAY_TRAMITE =
      "select exists (select 1 from solicitud_retracto where pedido_id = :id)"
          + " or exists (select 1 from reintegro where pedido_id = :id)"
          + " or exists (select 1 from solicitud_atencion where pedido_id = :id)"
          + " or exists (select 1 from reclamacion_garantia where pedido_id = :id)"
          + " or exists (select 1 from solicitud_reversion where pedido_id = :id)";

  private final NamedParameterJdbcTemplate jdbc;

  public BorradoDePedidosJdbc(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = Objects.requireNonNull(jdbc);
  }

  @Override
  public Set<Compromiso> compromisosDe(UUID pedidoId) {
    MapSqlParameterSource id = new MapSqlParameterSource("id", pedidoId);
    Set<Compromiso> compromisos = EnumSet.noneOf(Compromiso.class);
    if (Boolean.TRUE.equals(jdbc.queryForObject(HAY_PAGO_EN_JUEGO, id, Boolean.class))) {
      compromisos.add(Compromiso.PAGO);
    }
    if (Boolean.TRUE.equals(jdbc.queryForObject(HAY_ENVIO, id, Boolean.class))) {
      compromisos.add(Compromiso.ENVIO);
    }
    if (Boolean.TRUE.equals(jdbc.queryForObject(HAY_TRAMITE, id, Boolean.class))) {
      compromisos.add(Compromiso.TRAMITE);
    }
    return compromisos;
  }

  /**
   * Los intentos de pago primero —sus avisos de la pasarela caen en cascada—, y el pedido después,
   * que se lleva sus líneas y su historial en cascada (V4).
   *
   * <p>Los dos {@code delete} repiten la regla en el {@code where}, y no por desconfianza del caso
   * de uso: si entre la consulta y el borrado entró un pago pendiente, este no lo toca, y entonces
   * la llave de {@code pago} hacia el pedido hace fallar el segundo. Esa violación se traduce aquí,
   * sin volver a consultar la base (apps/api/CLAUDE.md: tras un flush fallido la sesión no sirve).
   */
  @Override
  public void eliminar(UUID pedidoId) {
    MapSqlParameterSource id = new MapSqlParameterSource("id", pedidoId);
    try {
      jdbc.update(
          "delete from pago where pedido_id = :id and estado in ('RECHAZADO', 'ERROR')", id);
      int borrados =
          jdbc.update(
              "delete from pedido where id = :id and estado in ('PAGO_FALLIDO', 'CANCELADO')", id);
      if (borrados != 1) {
        throw PedidoNoEliminableException.porCompromisos();
      }
    } catch (DataIntegrityViolationException e) {
      throw PedidoNoEliminableException.porCompromisos();
    }
  }
}
