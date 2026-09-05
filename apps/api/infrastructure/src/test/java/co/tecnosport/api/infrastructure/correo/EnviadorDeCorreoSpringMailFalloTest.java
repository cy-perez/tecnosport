package co.tecnosport.api.infrastructure.correo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import co.tecnosport.api.application.compartido.EnviadorDeCorreo;
import co.tecnosport.api.domain.compartido.CorreoElectronico;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.io.InputStream;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Sin Testcontainers ni Mailpit: {@link EnviadorDeCorreoSpringMailTest} ya cubre el camino feliz
 * contra un servidor SMTP real. Aquí solo hace falta un {@link JavaMailSender} que falle — un doble
 * de diez líneas, no Mockito (docs/06-testing.md).
 */
class EnviadorDeCorreoSpringMailFalloTest {

  @Test
  void unFalloAlEnviarSeRegistraYNoPropagaNiExponeElCorreoEnElLog() {
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    Logger logger = (Logger) LoggerFactory.getLogger(EnviadorDeCorreoSpringMail.class);
    appender.start();
    logger.addAppender(appender);

    try {
      EnviadorDeCorreo enviador =
          new EnviadorDeCorreoSpringMail(
              new JavaMailSenderQueFalla(), new PropiedadesCorreo("no-responder@tecnosport.co"));

      assertThatCode(
              () ->
                  enviador.enviar(
                      new CorreoElectronico("cliente@tecnosport.co"),
                      "Verifica tu correo",
                      "<p>Hola, verifica tu cuenta.</p>"))
          .doesNotThrowAnyException();

      assertThat(appender.list).hasSize(1);
      assertThat(appender.list.get(0).getFormattedMessage())
          .doesNotContain("cliente@tecnosport.co");
    } finally {
      logger.detachAppender(appender);
    }
  }

  private static final class JavaMailSenderQueFalla implements JavaMailSender {

    @Override
    public MimeMessage createMimeMessage() {
      return new MimeMessage(Session.getInstance(new Properties()));
    }

    @Override
    public MimeMessage createMimeMessage(InputStream contentStream) {
      throw new UnsupportedOperationException("no usado en esta prueba");
    }

    @Override
    public void send(MimeMessage... mimeMessages) {
      throw new MailSendException("simulado: SMTP no disponible");
    }

    @Override
    public void send(SimpleMailMessage simpleMessage) {
      throw new UnsupportedOperationException("no usado en esta prueba");
    }

    @Override
    public void send(SimpleMailMessage... simpleMessages) {
      throw new UnsupportedOperationException("no usado en esta prueba");
    }
  }
}
