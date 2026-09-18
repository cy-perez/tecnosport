package co.tecnosport.api.infrastructure.envio;

import co.tecnosport.api.application.envio.RepositorioEnvios;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.envio.Envio;
import co.tecnosport.api.domain.envio.EstadoEnvio;
import co.tecnosport.api.domain.envio.EventoSeguimiento;
import co.tecnosport.api.domain.envio.GuiaEnvio;
import co.tecnosport.api.domain.envio.ModalidadRecaudo;
import co.tecnosport.api.infrastructure.envio.entidad.EnvioJpaEntity;
import co.tecnosport.api.infrastructure.envio.entidad.EventoSeguimientoJpaEntity;
import co.tecnosport.api.infrastructure.envio.entidad.GuiaEnvioJpaEntity;
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
          id, guia_id, estado, descripcion, ocurrio_en, recibido_en, id_externo)
      values (:id, :guiaId, :estado, :descripcion, :ocurrioEn, :recibidoEn, :idExterno)
      on conflict (guia_id, id_externo) do nothing
      """;

  private final EnvioJpaRepository repositorio;
  private final GuiaEnvioJpaRepository guias;
  private final EventoSeguimientoJpaRepository eventos;
  private final NamedParameterJdbcTemplate jdbc;

  public RepositorioEnviosJpa(
      EnvioJpaRepository repositorio,
      GuiaEnvioJpaRepository guias,
      EventoSeguimientoJpaRepository eventos,
      NamedParameterJdbcTemplate jdbc) {
    this.repositorio = Objects.requireNonNull(repositorio);
    this.guias = Objects.requireNonNull(guias);
    this.eventos = Objects.requireNonNull(eventos);
    this.jdbc = Objects.requireNonNull(jdbc);
  }

  /**
   * {@code saveAndFlush} y no {@code save}, y no es cosmético: los eventos se insertan por JDBC
   * directo —para poder usar {@code on conflict}— mientras el envío y sus guías los escribe
   * Hibernate, que aplaza sus {@code insert} hasta el volcado. Sin forzarlo, la sentencia de JDBC
   * llega primero y revienta contra la clave foránea, porque para la base la guía todavía no
   * existe. Mezclar los dos caminos de escritura en la misma transacción obliga a ordenarlos a
   * mano.
   */
  @Override
  public void guardar(Envio envio) {
    repositorio.saveAndFlush(
        new EnvioJpaEntity(
            envio.id(),
            envio.pedidoId(),
            envio.despachadoEn(),
            envio.comisionRecaudo().map(Dinero::valor).orElse(null),
            envio.recaudoConciliadoEn().orElse(null),
            envio.modalidadRecaudo().map(Enum::name).orElse(null)));
    for (GuiaEnvio guia : envio.guias()) {
      guias.saveAndFlush(
          new GuiaEnvioJpaEntity(
              guia.id(),
              envio.id(),
              guia.transportadora(),
              guia.codigoTransportadora().orElse(null),
              guia.numero(),
              guia.costo().valor(),
              guia.urlEtiqueta().orElse(null)));
      for (EventoSeguimiento evento : guia.eventos()) {
        insertarSiNoEsta(guia.id(), evento);
      }
    }
  }

  private void insertarSiNoEsta(UUID guiaId, EventoSeguimiento evento) {
    Map<String, Object> parametros = new HashMap<>();
    parametros.put("id", evento.id());
    parametros.put("guiaId", guiaId);
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

  /**
   * Del número de guía al envío en dos saltos: la guía dice de quién es. El número es único en toda
   * la tabla —lo impone {@code uq_guia_envio_numero}—, que es justo lo que este método da por
   * cierto cuando resuelve un evento del webhook sin preguntar la transportadora.
   */
  @Override
  public Optional<Envio> buscarPorGuia(String guia) {
    return guias
        .findByNumero(guia)
        .flatMap(encontrada -> repositorio.findById(encontrada.getEnvioId()))
        .map(this::aEnvio);
  }

  @Override
  public List<Envio> buscarSinEventosDesde(Instant corte, int maximo) {
    return repositorio
        .buscarSinEventosDesde(corte, EstadoEnvio.nombresTerminales(), Limit.of(maximo))
        .stream()
        .map(this::aEnvio)
        .toList();
  }

  @Override
  public List<Envio> buscarConGuiasEnRevision(int maximo) {
    return repositorio
        .buscarConGuiasEnRevision(EstadoEnvio.nombresQueExigenRevisionManual(), Limit.of(maximo))
        .stream()
        .map(this::aEnvio)
        .toList();
  }

  private Envio aEnvio(EnvioJpaEntity entidad) {
    return new Envio(
        entidad.getId(),
        entidad.getPedidoId(),
        guias.findByEnvioIdOrderByNumeroAsc(entidad.getId()).stream().map(this::aGuia).toList(),
        entidad.getDespachadoEn(),
        entidad.getComisionRecaudo() == null ? null : Dinero.deCop(entidad.getComisionRecaudo()),
        entidad.getRecaudoConciliadoEn(),
        entidad.getModalidadRecaudo() == null
            ? null
            : ModalidadRecaudo.valueOf(entidad.getModalidadRecaudo()));
  }

  private GuiaEnvio aGuia(GuiaEnvioJpaEntity entidad) {
    return new GuiaEnvio(
        entidad.getId(),
        entidad.getTransportadora(),
        entidad.getCodigoTransportadora(),
        entidad.getNumero(),
        Dinero.deCop(entidad.getCostoEnvio()),
        entidad.getUrlEtiqueta(),
        eventos.findByGuiaIdOrderByOcurrioEnAsc(entidad.getId()).stream()
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
