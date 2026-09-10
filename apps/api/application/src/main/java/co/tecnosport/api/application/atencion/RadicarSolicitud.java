package co.tecnosport.api.application.atencion;

import co.tecnosport.api.application.compartido.EnviadorDeCorreo;
import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.domain.atencion.NumeroRadicado;
import co.tecnosport.api.domain.atencion.SolicitudAtencion;
import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.compartido.ZonaDelNegocio;
import java.time.Instant;
import java.util.Objects;

/**
 * Radica una petición, queja, reclamo o solicitud de datos que llegó por correo o por WhatsApp, que
 * son los canales que los términos publicados anuncian.
 *
 * <p>Radicar no es recibir. Lo que este caso de uso agrega a un correo leído son tres cosas que sin
 * él no existen: un número que el interesado puede citar, una fecha de llegada desde la que corre
 * el plazo, y el acuse que se lo comunica. El acuse es parte de la radicación y no un extra — sin
 * él, el número existiría solo del lado del negocio.
 *
 * <p>El acuse va dentro de la misma transacción, mismo criterio que {@code RegistrarRetracto}: si
 * el correo falla, la radicación tampoco se guarda. Dar por radicado algo que el interesado nunca
 * supo que se radicó es exactamente el reclamo que este registro pretende evitar.
 */
public final class RadicarSolicitud {

  private final RepositorioSolicitudesAtencion repositorio;
  private final EnviadorDeCorreo enviadorDeCorreo;
  private final Reloj reloj;

  public RadicarSolicitud(
      RepositorioSolicitudesAtencion repositorio, EnviadorDeCorreo enviadorDeCorreo, Reloj reloj) {
    this.repositorio = Objects.requireNonNull(repositorio);
    this.enviadorDeCorreo = Objects.requireNonNull(enviadorDeCorreo);
    this.reloj = Objects.requireNonNull(reloj);
  }

  public SolicitudAtencion ejecutar(RadicarSolicitudComando comando) {
    Objects.requireNonNull(comando, "El comando no puede ser nulo.");
    Instant ahora = reloj.ahora();
    Instant recibidaEn = comando.recibidaEn() == null ? ahora : comando.recibidaEn();
    CorreoElectronico correo = new CorreoElectronico(comando.correo());
    NumeroRadicado numeroRadicado =
        repositorio.siguienteRadicado(recibidaEn.atZone(ZonaDelNegocio.ZONA).getYear());

    SolicitudAtencion solicitud =
        SolicitudAtencion.radicar(
            numeroRadicado,
            comando.tipo(),
            correo,
            comando.pedidoId(),
            recibidaEn,
            ahora,
            comando.actor(),
            comando.asunto());
    repositorio.guardar(solicitud);
    enviarAcuse(solicitud);
    return solicitud;
  }

  /**
   * El año del radicado sale de la fecha de llegada y no de hoy: una solicitud de diciembre que se
   * registra en enero pertenece al año en que llegó, igual que su plazo.
   */
  private void enviarAcuse(SolicitudAtencion solicitud) {
    enviadorDeCorreo.enviar(
        solicitud.correo(),
        "Radicamos tu solicitud " + solicitud.numeroRadicado().valor() + " — TecnoSport",
        "<p>Radicamos tu solicitud con el numero <strong>"
            + solicitud.numeroRadicado().valor()
            + "</strong>. Guardalo: con ese numero puedes preguntarnos por ella cuando quieras.</p>"
            + "<p>Asunto: "
            + solicitud.asunto()
            + "</p>"
            + "<p>Te respondemos dentro del plazo que corresponde a tu solicitud. Si necesitamos "
            + "mas tiempo del previsto y la ley lo permite, te avisamos antes de que ese plazo "
            + "venza, con los motivos.</p>");
  }
}
