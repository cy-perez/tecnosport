package co.tecnosport.api.infrastructure.envio;

import co.tecnosport.api.application.envio.RepositorioEnvios;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.envio.Envio;
import co.tecnosport.api.domain.envio.EstadoEnvio;
import co.tecnosport.api.domain.envio.EventoSeguimiento;
import co.tecnosport.api.infrastructure.envio.entidad.EnvioJpaEntity;
import co.tecnosport.api.infrastructure.envio.entidad.EventoSeguimientoJpaEntity;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class RepositorioEnviosJpa implements RepositorioEnvios {

  /**
   * Una sentencia atómica por evento, con el mismo criterio que {@code
   * RepositorioPedidosJpa.SQL_SIGUIENTE_NUMERO}: el {@code on conflict} de Postgres serializa por
   * fila sin bloqueo pesimista explícito.
   *
   * <p>Antes esto era un {@code select} de los identificadores ya guardados y después un {@code
   * insert} de los que faltaban, y tenía una carrera con el nombre puesto: la plataforma reintenta
   * los webhooks, así que dos entregas del mismo evento son lo normal y no lo excepcional. Las dos
   * pasaban la comprobación, la segunda reventaba contra la restricción única, y la excepción subía
   * hasta el controlador convirtiendo en 500 un endpoint que promete 200 siempre.
   *
   * <p>{@code do nothing} y no {@code do update}: el rastro es append-only. Un evento que ya está
   * no se reescribe ni siquiera con datos idénticos, porque su {@code id} puede estar citado en una
   * reclamación.
   */
  private static final String SQL_INSERTAR_EVENTO =
      """
      insert into evento_seguimiento (
          id, envio_id, estado, descripcion, ocurrio_en, recibido_en, id_externo)
      values (:id, :envioId, :estado, :descripcion, :ocurrioEn, :recibidoEn, :idExterno)
      on conflict (envio_id, id_externo) do nothing
      """;

  private final EnvioJpaRepository repositorio;
  private final EventoSeguimientoJpaRepository eventos;
  private final NamedParameterJdbcTemplate jdbc;

  public RepositorioEnviosJpa(
      EnvioJpaRepository repositorio,
      EventoSeguimientoJpaRepository eventos,
      NamedParameterJdbcTemplate jdbc) {
    this.repositorio = Objects.requireNonNull(repositorio);
    this.eventos = Objects.requireNonNull(eventos);
    this.jdbc = Objects.requireNonNull(jdbc);
  }

  /**
   * {@code saveAndFlush} y no {@code save}, y no es cosmético: los eventos se insertan por JDBC
   * directo —para poder usar {@code on conflict}— mientras el envío lo escribe Hibernate, que
   * aplaza su {@code insert} hasta el volcado. Sin forzarlo, la sentencia de JDBC llega primero y
   * revienta contra la clave foránea, porque para la base el envío todavía no existe. Mezclar los
   * dos caminos de escritura en la misma transacción obliga a ordenarlos a mano.
   */
  @Override
  public void guardar(Envio envio) {
    repositorio.saveAndFlush(
        new EnvioJpaEntity(
            envio.id(),
            envio.pedidoId(),
            envio.transportadora(),
            envio.guia(),
            envio.costoEnvio().valor(),
            envio.despachadoEn(),
            envio.comisionRecaudo().map(Dinero::valor).orElse(null),
            envio.recaudoConciliadoEn().orElse(null)));
    for (EventoSeguimiento evento : envio.eventos()) {
      insertarSiNoEsta(envio.id(), evento);
    }
  }

  private void insertarSiNoEsta(UUID envioId, EventoSeguimiento evento) {
    Map<String, Object> parametros = new HashMap<>();
    parametros.put("id", evento.id());
    parametros.put("envioId", envioId);
    parametros.put("estado", evento.estado().name());
    parametros.put("descripcion", evento.descripcion());
    parametros.put("ocurrioEn", Timestamp.from(evento.ocurrioEn()));
    parametros.put("recibidoEn", Timestamp.from(evento.recibidoEn()));
    parametros.put("idExterno", evento.idExterno());
    jdbc.update(SQL_INSERTAR_EVENTO, parametros);
  }

  @Override
  public Optional<Envio> buscarPorPedidoId(UUID pedidoId) {
    return repositorio.findByPedidoId(pedidoId).map(this::aEnvio);
  }

  @Override
  public Optional<Envio> buscarPorGuia(String guia) {
    return repositorio.findByGuia(guia).map(this::aEnvio);
  }

  @Override
  public List<Envio> buscarSinEventosDesde(Instant corte, int maximo) {
    return repositorio
        .buscarSinEventosDesde(corte, EstadoEnvio.nombresTerminales(), Limit.of(maximo))
        .stream()
        .map(this::aEnvio)
        .toList();
  }

  private Envio aEnvio(EnvioJpaEntity entidad) {
    return new Envio(
        entidad.getId(),
        entidad.getPedidoId(),
        entidad.getTransportadora(),
        entidad.getGuia(),
        Dinero.deCop(entidad.getCostoEnvio()),
        entidad.getDespachadoEn(),
        entidad.getComisionRecaudo() == null ? null : Dinero.deCop(entidad.getComisionRecaudo()),
        entidad.getRecaudoConciliadoEn(),
        eventos.findByEnvioIdOrderByOcurrioEnAsc(entidad.getId()).stream()
            .map(this::aEvento)
            .toList());
  }

  private EventoSeguimiento aEvento(EventoSeguimientoJpaEntity entidad) {
    return new EventoSeguimiento(
        entidad.getId(),
        EstadoEnvio.valueOf(entidad.getEstado()),
        entidad.getDescripcion(),
        entidad.getOcurrioEn(),
        entidad.getRecibidoEn(),
        entidad.getIdExterno());
  }
}
