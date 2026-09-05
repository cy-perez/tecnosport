package co.tecnosport.api.infrastructure.correo;

import co.tecnosport.api.application.compartido.EnviadorDeCorreo;
import co.tecnosport.api.domain.compartido.CorreoElectronico;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.util.Objects;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

/**
 * Contra Mailpit en local (docker-compose.yml, {@code SMTP_HOST=localhost}) y SMTP real en
 * producción ({@code SMTP_HOST}/{@code SMTP_USUARIO}/{@code SMTP_CLAVE}, docs/07-infra-gcp.md).
 * {@code JavaMailSender} y su autoconfiguración los resuelve Spring Boot solo desde {@code
 * spring.mail.*} en {@code application.yml} — no hace falta un cliente propio.
 */
public class EnviadorDeCorreoSpringMail implements EnviadorDeCorreo {

  private final JavaMailSender mailSender;
  private final PropiedadesCorreo propiedades;

  public EnviadorDeCorreoSpringMail(JavaMailSender mailSender, PropiedadesCorreo propiedades) {
    this.mailSender = Objects.requireNonNull(mailSender);
    this.propiedades = Objects.requireNonNull(propiedades);
  }

  @Override
  public void enviar(CorreoElectronico destinatario, String asunto, String cuerpoHtml) {
    MimeMessage mensaje = mailSender.createMimeMessage();
    try {
      MimeMessageHelper helper = new MimeMessageHelper(mensaje, false, "UTF-8");
      helper.setFrom(propiedades.remitente());
      helper.setTo(destinatario.valor());
      helper.setSubject(asunto);
      helper.setText(cuerpoHtml, true);
    } catch (MessagingException excepcion) {
      throw new MailSendException(
          "No se pudo componer el correo para " + destinatario.valor() + ".", excepcion);
    }
    mailSender.send(mensaje);
  }
}
