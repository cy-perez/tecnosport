package co.tecnosport.api.infrastructure.correo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import co.tecnosport.api.application.compartido.CorreoNoEnviadoException;
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
 *
 * <p><b>Esta clase afirmaba lo contrario hasta {@code adr/0044}</b>, y conviene que quede dicho: su
 * única prueba se llamaba {@code unFalloAlEnviarSeRegistraYNoPropaga} y exigía {@code
 * doesNotThrowAnyException()}. Estaba en verde, y lo que protegía era el defecto — que el adaptador
 * decidiera por sus doce llamadores que un correo perdido no le importa a nadie. Una prueba puede
 * fijar un error tan bien como fija un acierto.
 */
class EnviadorDeCorreoSpringMailFalloTest {

  @Test
  void unFalloAlEnviarSeRegistraYSePropagaSinExponerElCorreo() {
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    Logger logger = (Logger) LoggerFactory.getLogger(EnviadorDeCorreoSpringMail.class);
    appender.start();
    logger.addAppender(appender);

    try {
      EnviadorDeCorreo enviador =
          new EnviadorDeCorreoSpringMail(
              new JavaMailSenderQueFalla(), new PropiedadesCorreo("no-responder@tecnosport.co"));

      // Se propaga: es de lo que depende que EnviarComprobantesDeCompra pueda devolver el reclamo
      // y reintentar. Sin esto, su catch está escrito y no se ejecuta nunca.
      assertThatThrownBy(
              () ->
                  enviador.enviar(
                      new CorreoElectronico("cliente@tecnosport.co"),
                      "Verifica tu correo",
                      "<p>Hola, verifica tu cuenta.</p>"))
          .isInstanceOf(CorreoNoEnviadoException.class)
          // La causa viaja: sin ella, quien lea el registro no sabe si fue el servidor, la red o
          // las credenciales, que es lo único accionable de un fallo de SMTP.
          .hasCauseInstanceOf(MailSendException.class)
          .hasMessageNotContaining("cliente@tecnosport.co");

      // Y se registra además de lanzarse, que no es redundante: application no tiene slf4j en el
      // classpath, así que los casos de uso que deciden tragarla no pueden dejar constancia de
      // nada. Esta línea es la única señal que queda de ellos.
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
