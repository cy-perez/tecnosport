package co.tecnosport.api.application.atencion;

import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.domain.atencion.Respuesta;
import co.tecnosport.api.domain.atencion.SolicitudAtencion;
import java.util.Objects;

/**
 * Cierra una solicitud dejando constancia de qué se contestó y cuándo.
 *
 * <p>No envía la respuesta: esa sale por el mismo canal por el que llegó la solicitud —el correo o
 * el WhatsApp que anuncian los términos— y quien la escribe es una persona. Lo que falta sin este
 * caso de uso no es el envío, es la prueba de que hubo respuesta y de que llegó a tiempo, que es lo
 * que hay que poder mostrar cuando alguien lo discuta.
 */
public final class ResponderSolicitud {

  private final RepositorioSolicitudesAtencion repositorio;
  private final Reloj reloj;

  public ResponderSolicitud(RepositorioSolicitudesAtencion repositorio, Reloj reloj) {
    this.repositorio = Objects.requireNonNull(repositorio);
    this.reloj = Objects.requireNonNull(reloj);
  }

  public SolicitudAtencion ejecutar(ResponderSolicitudComando comando) {
    Objects.requireNonNull(comando, "El comando no puede ser nulo.");
    SolicitudAtencion solicitud =
        repositorio
            .buscarPorId(comando.solicitudId())
            .orElseThrow(() -> new SolicitudAtencionNoEncontradaException(comando.solicitudId()));
    solicitud.responder(new Respuesta(reloj.ahora(), comando.actor(), comando.resumen()));
    repositorio.guardar(solicitud);
    return solicitud;
  }
}
