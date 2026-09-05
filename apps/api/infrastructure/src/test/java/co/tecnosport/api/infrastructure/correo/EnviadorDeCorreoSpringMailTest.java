package co.tecnosport.api.infrastructure.correo;

import static org.assertj.core.api.Assertions.assertThat;

import co.tecnosport.api.application.compartido.EnviadorDeCorreo;
import co.tecnosport.api.domain.compartido.CorreoElectronico;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Contra un Mailpit real en un contenedor, no un {@code JavaMailSender} de prueba — mismo criterio
 * que el resto de {@code infrastructure}: Testcontainers con el sistema real, no un doble
 * (`docs/06-testing.md`). Se verifica por la API HTTP de Mailpit, que expone lo que su propio
 * servidor SMTP recibió.
 *
 * <p>{@code JavaMailSender} lo autoconfigura Spring Boot solo desde {@code spring.mail.*}, sin
 * pasar por {@code ConfiguracionCorreo} — esa clase vive en {@code bootstrap}, que {@code
 * infrastructure} no puede ver ni en pruebas (ver {@code ConfiguracionDePruebasInfraestructura}).
 * Por eso {@link EnviadorDeCorreoSpringMail} se construye a mano aquí, igual que cualquier otro
 * adaptador de esta capa que no pasa por un {@code @Bean} de bootstrap en su propia prueba.
 */
@SpringBootTest
@Testcontainers
class EnviadorDeCorreoSpringMailTest {

  @Container @ServiceConnection
  static PostgreSQLContainer postgres =
      new PostgreSQLContainer(DockerImageName.parse("postgres:16"));

  @Container
  static GenericContainer<?> mailpit =
      new GenericContainer<>(DockerImageName.parse("axllent/mailpit")).withExposedPorts(1025, 8025);

  @DynamicPropertySource
  static void propiedadesCorreo(DynamicPropertyRegistry registro) {
    registro.add("spring.mail.host", mailpit::getHost);
    registro.add("spring.mail.port", () -> mailpit.getMappedPort(1025));
  }

  @Autowired private JavaMailSender mailSender;

  @Test
  void unCorreoEnviadoLlegaAlServidorSmtp() throws IOException, InterruptedException {
    EnviadorDeCorreo enviador =
        new EnviadorDeCorreoSpringMail(
            mailSender, new PropiedadesCorreo("no-responder@tecnosport.co"));

    enviador.enviar(
        new CorreoElectronico("cliente@tecnosport.co"),
        "Verifica tu correo",
        "<p>Hola, verifica tu cuenta.</p>");

    String urlMensajes =
        "http://" + mailpit.getHost() + ":" + mailpit.getMappedPort(8025) + "/api/v1/messages";
    HttpResponse<String> respuesta =
        HttpClient.newHttpClient()
            .send(
                HttpRequest.newBuilder(URI.create(urlMensajes)).GET().build(),
                HttpResponse.BodyHandlers.ofString());

    assertThat(respuesta.statusCode()).isEqualTo(200);
    assertThat(respuesta.body())
        .contains("Verifica tu correo")
        .contains("cliente@tecnosport.co")
        .contains("no-responder@tecnosport.co");
  }
}
