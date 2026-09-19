package co.tecnosport.api.infrastructure.correo;

import co.tecnosport.api.application.compartido.CorreoNoEnviadoException;
import co.tecnosport.api.application.compartido.EnviadorDeCorreo;
import co.tecnosport.api.domain.compartido.CorreoElectronico;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

/**
 * Contra Mailpit en local (docker-compose.yml, {@code SMTP_HOST=localhost}) y SMTP real en
 * producción ({@code SMTP_HOST}/{@code SMTP_USUARIO}/{@code SMTP_CLAVE}, docs/07-infra-gcp.md).
 * {@code JavaMailSender} y su autoconfiguración los resuelve Spring Boot solo desde {@code
 * spring.mail.*} en {@code application.yml} — no hace falta un cliente propio.
 *
 * <p><b>Un fallo al componer o enviar sale de aquí como {@link CorreoNoEnviadoException}</b>, y
 * durante cuatro fases no fue así: se registraba y se tragaba. La razón por la que se tragaba era
 * buena —{@code SolicitarRecuperacion} responde 204 exista o no la cuenta, y un 500 solo cuando la
 * cuenta sí existe sería justo el oráculo de enumeración que ese diseño evita— pero el sitio estaba
 * equivocado: <b>tragarlo aquí lo decidía para los doce llamadores</b>, y unos cuantos necesitan
 * enterarse. El que más, {@code EnviarComprobantesDeCompra}: reclama el comprobante antes de
 * mandarlo, y sin excepción que atrapar dejaba la marca puesta con el correo sin salir — o sea, ese
 * comprador sin el documento de su compra para siempre. Ahora cada caso de uso decide, y los dos
 * que tragan lo dicen en su propio código con el motivo al lado. Ver {@code adr/0044}.
 *
 * <p><b>Y registra además de lanzar</b>, que parece redundante y no lo es: {@code application} no
 * tiene slf4j en el classpath —solo declara {@code :domain}, regla dura #1— así que un caso de uso
 * que decide tragar la excepción no puede dejar constancia de nada. Si el adaptador no registrara,
 * los dos caminos que tragan por diseño serían otra vez un silencio. Aquí el registro es la única
 * señal que queda de ellos, y en los que reintentan cuenta los intentos.
 *
 * <p>Ni el mensaje ni el registro llevan el correo del destinatario: docs/08-seguridad-legal.md,
 * registros sin datos personales ni tokens.
 */
public class EnviadorDeCorreoSpringMail implements EnviadorDeCorreo {

  private static final Logger log = LoggerFactory.getLogger(EnviadorDeCorreoSpringMail.class);

  private final JavaMailSender mailSender;
  private final PropiedadesCorreo propiedades;

  public EnviadorDeCorreoSpringMail(JavaMailSender mailSender, PropiedadesCorreo propiedades) {
    this.mailSender = Objects.requireNonNull(mailSender);
    this.propiedades = Objects.requireNonNull(propiedades);
  }

  @Override
  public void enviar(CorreoElectronico destinatario, String asunto, String cuerpoHtml) {
    try {
      MimeMessage mensaje = mailSender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(mensaje, false, "UTF-8");
      helper.setFrom(propiedades.remitente());
      helper.setTo(destinatario.valor());
      helper.setSubject(asunto);
      helper.setText(cuerpoHtml, true);
      mailSender.send(mensaje);
    } catch (MessagingException | MailException excepcion) {
      log.error("No se pudo enviar un correo transaccional.", excepcion);
      throw new CorreoNoEnviadoException(excepcion);
    }
  }
}
