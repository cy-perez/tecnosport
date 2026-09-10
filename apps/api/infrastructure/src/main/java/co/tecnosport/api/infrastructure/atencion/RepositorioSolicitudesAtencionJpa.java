package co.tecnosport.api.infrastructure.atencion;

import co.tecnosport.api.application.atencion.RepositorioSolicitudesAtencion;
import co.tecnosport.api.domain.atencion.EstadoSolicitudAtencion;
import co.tecnosport.api.domain.atencion.NumeroRadicado;
import co.tecnosport.api.domain.atencion.Prorroga;
import co.tecnosport.api.domain.atencion.Respuesta;
import co.tecnosport.api.domain.atencion.SolicitudAtencion;
import co.tecnosport.api.domain.atencion.TipoSolicitud;
import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.infrastructure.atencion.entidad.SolicitudAtencionJpaEntity;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Sin transacción propia, mismo criterio que el resto de repositorios del proyecto: comparte la de
 * quien lo llama.
 *
 * <p>{@code siguienteRadicado} reclama el secuencial con un único {@code insert ... on conflict ...
 * returning}, copiado de {@code RepositorioPedidosJpa.siguienteNumero} — sin bloqueo pesimista
 * explícito y sin hueco entre leer y escribir.
 */
@Component
public class RepositorioSolicitudesAtencionJpa implements RepositorioSolicitudesAtencion {

  private static final String SQL_SIGUIENTE_RADICADO =
      """
      insert into secuencia_radicado (anio, siguiente) values (:anio, 2)
      on conflict (anio) do update set siguiente = secuencia_radicado.siguiente + 1
      returning siguiente - 1
      """;

  private final SolicitudAtencionJpaRepository repositorio;
  private final NamedParameterJdbcTemplate jdbc;

  public RepositorioSolicitudesAtencionJpa(
      SolicitudAtencionJpaRepository repositorio, NamedParameterJdbcTemplate jdbc) {
    this.repositorio = Objects.requireNonNull(repositorio);
    this.jdbc = Objects.requireNonNull(jdbc);
  }

  @Override
  public void guardar(SolicitudAtencion solicitud) {
    Prorroga prorroga = solicitud.prorroga().orElse(null);
    Respuesta respuesta = solicitud.respuesta().orElse(null);
    repositorio.save(
        new SolicitudAtencionJpaEntity(
            solicitud.id(),
            solicitud.numeroRadicado().valor(),
            solicitud.tipo().name(),
            solicitud.correo().valor(),
            solicitud.pedidoId().orElse(null),
            solicitud.recibidaEn(),
            solicitud.radicadaEn(),
            solicitud.radicadaPor(),
            solicitud.asunto(),
            solicitud.estado().name(),
            prorroga == null ? null : prorroga.otorgadaEn(),
            prorroga == null ? null : prorroga.otorgadaPor(),
            prorroga == null ? null : prorroga.motivo(),
            prorroga == null ? null : prorroga.avisadaEn(),
            respuesta == null ? null : respuesta.respondidaEn(),
            respuesta == null ? null : respuesta.respondidaPor(),
            respuesta == null ? null : respuesta.resumen()));
  }

  @Override
  public Optional<SolicitudAtencion> buscarPorId(UUID id) {
    return repositorio.findById(id).map(this::aSolicitud);
  }

  @Override
  public Optional<SolicitudAtencion> buscarPorRadicado(NumeroRadicado numeroRadicado) {
    return repositorio.findByNumeroRadicado(numeroRadicado.valor()).map(this::aSolicitud);
  }

  @Override
  public List<SolicitudAtencion> buscarPorPedidoId(UUID pedidoId) {
    return repositorio.findByPedidoIdOrderByRecibidaEnDesc(pedidoId).stream()
        .map(this::aSolicitud)
        .toList();
  }

  @Override
  public List<SolicitudAtencion> buscarPorEstado(EstadoSolicitudAtencion estado) {
    return repositorio.findByEstadoOrderByRecibidaEnAsc(estado.name()).stream()
        .map(this::aSolicitud)
        .toList();
  }

  @Override
  public List<SolicitudAtencion> buscarAbiertas() {
    return repositorio
        .findByEstadoNotOrderByRecibidaEnAsc(EstadoSolicitudAtencion.RESPONDIDA.name())
        .stream()
        .map(this::aSolicitud)
        .toList();
  }

  @Override
  public NumeroRadicado siguienteRadicado(int anio) {
    Long secuencial =
        jdbc.queryForObject(
            SQL_SIGUIENTE_RADICADO, new MapSqlParameterSource("anio", anio), Long.class);
    return NumeroRadicado.de(anio, secuencial);
  }

  private SolicitudAtencion aSolicitud(SolicitudAtencionJpaEntity entidad) {
    return new SolicitudAtencion(
        entidad.getId(),
        new NumeroRadicado(entidad.getNumeroRadicado()),
        TipoSolicitud.valueOf(entidad.getTipo()),
        new CorreoElectronico(entidad.getCorreo()),
        entidad.getPedidoId(),
        entidad.getRecibidaEn(),
        entidad.getRadicadaEn(),
        entidad.getRadicadaPor(),
        entidad.getAsunto(),
        EstadoSolicitudAtencion.valueOf(entidad.getEstado()),
        entidad.getProrrogaOtorgadaEn() == null
            ? null
            : new Prorroga(
                entidad.getProrrogaOtorgadaEn(),
                entidad.getProrrogaOtorgadaPor(),
                entidad.getProrrogaMotivo(),
                entidad.getProrrogaAvisadaEn()),
        entidad.getRespuestaEn() == null
            ? null
            : new Respuesta(
                entidad.getRespuestaEn(),
                entidad.getRespuestaPor(),
                entidad.getRespuestaResumen()));
  }
}
