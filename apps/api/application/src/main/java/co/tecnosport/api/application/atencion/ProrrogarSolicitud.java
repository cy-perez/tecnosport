package co.tecnosport.api.application.atencion;

import co.tecnosport.api.application.compartido.EnviadorDeCorreo;
import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.domain.atencion.PlazosDeAtencion;
import co.tecnosport.api.domain.atencion.Prorroga;
import co.tecnosport.api.domain.atencion.SolicitudAtencion;
import co.tecnosport.api.domain.compartido.CalendarioHabil;
import java.time.Instant;
import java.util.Objects;

/**
 * Prorroga el plazo de respuesta y avisa al interesado, en ese orden y sin separarlos.
 *
 * <p>La ley no concede días extra por pedirlos: los concede si se informa al interesado con sus
 * motivos antes de que venza el plazo inicial. Por eso el aviso no es un paso posterior que alguien
 * pueda olvidar — se manda aquí, con la misma fecha que queda escrita en la prórroga, y si el
 * correo falla no queda prórroga registrada. Una prórroga que nadie avisó no es una prórroga.
 *
 * <p>Que el aviso llegue a tiempo lo comprueba el agregado, no este caso de uso: es una regla del
 * plazo, no de la orquestación.
 */
public final class ProrrogarSolicitud {

  private final RepositorioSolicitudesAtencion repositorio;
  private final PlazosDeAtencion plazos;
  private final CalendarioHabil calendario;
  private final EnviadorDeCorreo enviadorDeCorreo;
  private final Reloj reloj;

  public ProrrogarSolicitud(
      RepositorioSolicitudesAtencion repositorio,
      PlazosDeAtencion plazos,
      CalendarioHabil calendario,
      EnviadorDeCorreo enviadorDeCorreo,
      Reloj reloj) {
    this.repositorio = Objects.requireNonNull(repositorio);
    this.plazos = Objects.requireNonNull(plazos);
    this.calendario = Objects.requireNonNull(calendario);
    this.enviadorDeCorreo = Objects.requireNonNull(enviadorDeCorreo);
    this.reloj = Objects.requireNonNull(reloj);
  }

  public SolicitudAtencion ejecutar(ProrrogarSolicitudComando comando) {
    Objects.requireNonNull(comando, "El comando no puede ser nulo.");
    SolicitudAtencion solicitud =
        repositorio
            .buscarPorId(comando.solicitudId())
            .orElseThrow(() -> new SolicitudAtencionNoEncontradaException(comando.solicitudId()));

    Instant ahora = reloj.ahora();
    solicitud.prorrogar(
        new Prorroga(ahora, comando.actor(), comando.motivo(), ahora), plazos, calendario);
    repositorio.guardar(solicitud);
    avisar(solicitud, comando.motivo());
    return solicitud;
  }

  private void avisar(SolicitudAtencion solicitud, String motivo) {
    enviadorDeCorreo.enviar(
        solicitud.correo(),
        "Necesitamos mas tiempo con tu solicitud "
            + solicitud.numeroRadicado().valor()
            + " — TecnoSport",
        "<p>Sobre tu solicitud <strong>"
            + solicitud.numeroRadicado().valor()
            + "</strong>: necesitamos mas tiempo para responderte de fondo, y la ley nos permite "
            + "prorrogar el plazo avisandote antes de que venza, con los motivos.</p>"
            + "<p>Motivo: "
            + motivo
            + "</p>"
            + "<p>Te respondemos dentro del plazo prorrogado.</p>");
  }
}
