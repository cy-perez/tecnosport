package co.tecnosport.api.infrastructure.correo;

import co.tecnosport.api.application.compartido.CorreoNoEnviadoException;
import co.tecnosport.api.application.compartido.TransporteDeCorreo;
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
 * <p><b>Ya no lo llama ningún caso de uso.</b> Hasta {@code adr/0045} esto implementaba {@code
 * EnviadorDeCorreo} y era el correo del sistema entero; ahora implementa {@link TransporteDeCorreo}
 * y lo llama un solo sitio, {@code DrenarBandejaDeSalida}. Quien quiera mandar un correo escribe
 * una fila en la bandeja y sigue con lo suyo.
 *
 * <p>Su historia conviene no perderla, porque explica por qué el puerto está partido en dos.
 * Durante cuatro fases este adaptador <b>registraba el fallo y se lo tragaba</b>. La razón por la
 * que se tragaba era buena —{@code SolicitarRecuperacion} responde 204 exista o no la cuenta, y un
 * 500 solo cuando la cuenta sí existe sería el oráculo de enumeración que ese diseño evita— pero el
 * sitio estaba equivocado: tragarlo aquí lo decidía para los doce llamadores. {@code adr/0044} lo
 * puso a lanzar y devolvió la decisión a cada caso de uso; {@code adr/0045} se dio cuenta de que
 * esa decisión, en realidad, no era de ninguno de ellos — es de la bandeja, y se llama reintentar.
 *
 * <p><b>Registra además de lanzar</b>, y sigue sin ser redundante: {@code application} no tiene
 * slf4j en el classpath (solo declara {@code :domain}, regla dura #1), así que la única constancia
 * inmediata de un SMTP caído es esta línea. El detalle del fallo también queda en la fila, en
 * {@code ultimo_error}.
 *
 * <p>Ni el mensaje ni el registro llevan el correo del destinatario: docs/08-seguridad-legal.md,
 * registros sin datos personales ni tokens.
 */
public class TransporteDeCorreoSpringMail implements TransporteDeCorreo {

  private static final Logger log = LoggerFactory.getLogger(TransporteDeCorreoSpringMail.class);

  private final JavaMailSender mailSender;
  private final PropiedadesCorreo propiedades;

  public TransporteDeCorreoSpringMail(JavaMailSender mailSender, PropiedadesCorreo propiedades) {
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
      // Sin volcar la excepción entera, y sin su mensaje crudo: un rechazo de SMTP repite la
      // dirección —`550 5.1.1 <cliente@ejemplo.com>: Recipient address rejected`— y el javadoc de
      // arriba promete que el registro no lleva el correo del destinatario. La promesa escrita era
      // más fuerte que el código. Lo que sirve para diagnosticar es el código del rechazo, y ese
      // se queda; el detalle completo vive en la columna `ultimo_error` de la bandeja, que es una
      // tabla y no un registro, y ahí el destinatario ya está en su propia columna.
      log.error(
          "No se pudo enviar un correo transaccional: {}",
          RedaccionDeCorreos.sinCorreos(
              excepcion.getClass().getSimpleName() + ": " + excepcion.getMessage()));
      throw new CorreoNoEnviadoException(excepcion);
    }
  }
}
