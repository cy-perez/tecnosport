package co.tecnosport.api.infrastructure.correo;

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
 * <p>Un fallo al componer o enviar el correo se registra y se traga aquí, no se relanza: {@code
 * SolicitarRecuperacion} responde 204 siempre, exista o no una cuenta con ese correo (OWASP,
 * anti-enumeración) — si un fallo de SMTP transitorio revienta como 500 solo cuando la cuenta sí
 * existe (porque solo ahí se llega a intentar el envío), esa diferencia de código de estado es
 * exactamente el oráculo que el diseño evita. Mismo criterio para {@code RegistrarUsuario}: la
 * cuenta ya quedó creada, un correo que no salió no debería tumbar la respuesta. Ninguno de los dos
 * casos de uso necesita enterarse de si el envío falló — por eso el puerto no lo modela.
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
      // Sin el correo del destinatario en el mensaje: docs/08-seguridad-legal.md, registros sin
      // datos personales ni tokens.
      log.error("No se pudo enviar un correo transaccional.", excepcion);
    }
  }
}
