package co.tecnosport.api.application.atencion;

import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.domain.atencion.EstadoSolicitudAtencion;
import co.tecnosport.api.domain.atencion.PlazosDeAtencion;
import co.tecnosport.api.domain.atencion.SolicitudAtencion;
import co.tecnosport.api.domain.compartido.CalendarioHabil;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * La bandeja: qué hay abierto y cuánto falta para que venza cada cosa.
 *
 * <p>Existe porque un plazo que solo vive en una columna no lo cumple nadie. La política de datos y
 * los términos publicados prometen responder dentro de unos días hábiles, y demostrar que se
 * cumplió exige, antes que nada, que alguien pueda ver lo que está por vencerse sin consultar la
 * base de datos a mano.
 *
 * <p>Ordena por límite ascendente y no por fecha de radicación: dos solicitudes radicadas el mismo
 * día vencen en días distintos si son de tipos distintos, y lo que hay que atender primero es lo
 * que vence antes, no lo que llegó antes.
 */
public final class ListarSolicitudesDeAtencion {

  private final RepositorioSolicitudesAtencion repositorio;
  private final PlazosDeAtencion plazos;
  private final CalendarioHabil calendario;
  private final Reloj reloj;

  public ListarSolicitudesDeAtencion(
      RepositorioSolicitudesAtencion repositorio,
      PlazosDeAtencion plazos,
      CalendarioHabil calendario,
      Reloj reloj) {
    this.repositorio = Objects.requireNonNull(repositorio);
    this.plazos = Objects.requireNonNull(plazos);
    this.calendario = Objects.requireNonNull(calendario);
    this.reloj = Objects.requireNonNull(reloj);
  }

  /**
   * Una sola solicitud con su plazo resuelto, para quien acaba de radicarla o de responderla. Vive
   * aquí y no en presentación porque el límite depende del calendario y de los plazos configurados,
   * que son decisiones de aplicación.
   */
  public SolicitudConPlazo conPlazo(SolicitudAtencion solicitud) {
    Instant ahora = reloj.ahora();
    return new SolicitudConPlazo(
        solicitud,
        solicitud.limiteDeRespuesta(plazos, calendario),
        solicitud.verdictoDeRespuesta(ahora, plazos, calendario));
  }

  /** {@code estado} nulo trae lo abierto, que es lo que la bandeja muestra por omisión. */
  public List<SolicitudConPlazo> ejecutar(EstadoSolicitudAtencion estado) {
    List<SolicitudAtencion> solicitudes =
        estado == null ? repositorio.buscarAbiertas() : repositorio.buscarPorEstado(estado);
    Instant ahora = reloj.ahora();
    return solicitudes.stream()
        .map(
            solicitud ->
                new SolicitudConPlazo(
                    solicitud,
                    solicitud.limiteDeRespuesta(plazos, calendario),
                    solicitud.verdictoDeRespuesta(ahora, plazos, calendario)))
        .sorted(Comparator.comparing(SolicitudConPlazo::limiteDeRespuesta))
        .toList();
  }

  public List<SolicitudConPlazo> deUnPedido(UUID pedidoId) {
    Instant ahora = reloj.ahora();
    return repositorio.buscarPorPedidoId(pedidoId).stream()
        .map(
            solicitud ->
                new SolicitudConPlazo(
                    solicitud,
                    solicitud.limiteDeRespuesta(plazos, calendario),
                    solicitud.verdictoDeRespuesta(ahora, plazos, calendario)))
        .sorted(Comparator.comparing(SolicitudConPlazo::limiteDeRespuesta))
        .toList();
  }
}
