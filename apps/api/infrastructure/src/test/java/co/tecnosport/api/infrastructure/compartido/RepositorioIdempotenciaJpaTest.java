package co.tecnosport.api.infrastructure.compartido;

import static org.assertj.core.api.Assertions.assertThat;

import co.tecnosport.api.application.compartido.RespuestaIdempotente;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

// @Transactional de clase: cada prueba en su propia transacción, revertida al terminar — el
// adaptador abre @Transactional por método, pero por defecto se une a la transacción que ya está
// activa (propagación REQUIRED), así que esto no cambia lo que se prueba. Mismo criterio que
// RepositorioCarritoJpaTest.
@SpringBootTest
@Testcontainers
@Transactional
class RepositorioIdempotenciaJpaTest {

  @Container @ServiceConnection
  static PostgreSQLContainer postgres =
      new PostgreSQLContainer(DockerImageName.parse("postgres:16"));

  @Autowired private RepositorioIdempotenciaJpa repositorio;

  private static final Instant AHORA = Instant.parse("2026-09-02T12:00:00Z");

  private String llaveNueva() {
    return UUID.randomUUID().toString();
  }

  @Test
  void reclamarUnaLlaveNuevaDevuelveTrue() {
    boolean reclamada = repositorio.reclamar(llaveNueva(), "POST", "/api/v1/pedidos", AHORA);

    assertThat(reclamada).isTrue();
  }

  @Test
  void reclamarLaMismaLlaveDeNuevoDevuelveFalse() {
    String llave = llaveNueva();
    repositorio.reclamar(llave, "POST", "/api/v1/pedidos", AHORA);

    boolean segundaVez = repositorio.reclamar(llave, "POST", "/api/v1/pedidos", AHORA);

    assertThat(segundaVez).isFalse();
  }

  @Test
  void buscarCompletadaAntesDeCompletarDevuelveVacio() {
    String llave = llaveNueva();
    repositorio.reclamar(llave, "POST", "/api/v1/pedidos", AHORA);

    Optional<RespuestaIdempotente> encontrada = repositorio.buscarCompletada(llave, AHORA);

    assertThat(encontrada).isEmpty();
  }

  @Test
  void completarYBuscarDevuelveLaRespuestaGuardada() {
    String llave = llaveNueva();
    repositorio.reclamar(llave, "POST", "/api/v1/pedidos", AHORA);

    repositorio.completar(
        llave, new RespuestaIdempotente(200, "application/json", "{\"id\":\"abc\"}"), AHORA);

    RespuestaIdempotente encontrada = repositorio.buscarCompletada(llave, AHORA).orElseThrow();
    assertThat(encontrada.estadoHttp()).isEqualTo(200);
    assertThat(encontrada.tipoContenido()).isEqualTo("application/json");
    assertThat(encontrada.cuerpo()).isEqualTo("{\"id\":\"abc\"}");
  }

  @Test
  void buscarCompletadaYaVencidaDevuelveVacio() {
    String llave = llaveNueva();
    repositorio.reclamar(llave, "POST", "/api/v1/pedidos", AHORA);
    repositorio.completar(llave, new RespuestaIdempotente(200, "application/json", "{}"), AHORA);

    Instant muchoDespues = AHORA.plus(Duration.ofHours(25));
    Optional<RespuestaIdempotente> encontrada = repositorio.buscarCompletada(llave, muchoDespues);

    assertThat(encontrada).isEmpty();
  }

  @Test
  void reclamarUnaLlaveCompletadaYVencidaVuelveAReclamarla() {
    String llave = llaveNueva();
    repositorio.reclamar(llave, "POST", "/api/v1/pedidos", AHORA);
    repositorio.completar(llave, new RespuestaIdempotente(200, "application/json", "{}"), AHORA);

    Instant muchoDespues = AHORA.plus(Duration.ofHours(25));
    boolean reclamadaDeNuevo = repositorio.reclamar(llave, "POST", "/api/v1/pedidos", muchoDespues);

    assertThat(reclamadaDeNuevo).isTrue();
  }

  @Test
  void liberarPermiteReclamarDeNuevoInmediatamente() {
    String llave = llaveNueva();
    repositorio.reclamar(llave, "POST", "/api/v1/pedidos", AHORA);

    repositorio.liberar(llave);

    boolean reclamadaDeNuevo = repositorio.reclamar(llave, "POST", "/api/v1/pedidos", AHORA);
    assertThat(reclamadaDeNuevo).isTrue();
  }
}
