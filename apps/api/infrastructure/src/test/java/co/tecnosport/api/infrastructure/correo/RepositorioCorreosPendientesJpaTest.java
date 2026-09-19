package co.tecnosport.api.infrastructure.correo;

import static org.assertj.core.api.Assertions.assertThat;

import co.tecnosport.api.application.compartido.CorreoPendiente;
import co.tecnosport.api.infrastructure.correo.entidad.CorreoPendienteJpaEntity;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * <b>Sin {@code @Transactional} de clase, al revés que {@code RepositorioIdempotenciaJpaTest}</b>,
 * y por lo mismo que {@code RepositorioEmisionesJpaGuardarTest}: con una transacción de prueba
 * envolviéndolo todo no se puede observar qué queda comprometido, que es justo lo que hay que
 * comprobar de un reclamo entre instancias. Cada prueba limpia lo suyo.
 *
 * <p>Y hace falta contra Postgres de verdad, no contra un doble: {@code adr/0044} dejó escrito que
 * el mecanismo que impedía el correo duplicado <b>no tenía prueba contra Postgres</b>, y que la de
 * aplicación pasaba igual con el SQL borrado porque su reclamo era un {@code Set.add()}. Esta es la
 * que no pasa si la sentencia condicional se estropea.
 */
@SpringBootTest
@Testcontainers
class RepositorioCorreosPendientesJpaTest {

  @Container @ServiceConnection
  static PostgreSQLContainer postgres =
      new PostgreSQLContainer(DockerImageName.parse("postgres:16"));

  private static final Instant AHORA = Instant.parse("2026-09-19T12:00:00Z");

  @Autowired private RepositorioCorreosPendientesJpa repositorio;
  @Autowired private CorreoPendienteJpaRepository correos;

  @Test
  void unCorreoEncoladoSaleEnLosEnviables() {
    UUID id = encolar(AHORA, 0);

    List<CorreoPendiente> enviables = repositorio.buscarEnviables(5, AHORA, 100);

    assertThat(enviables).extracting(CorreoPendiente::id).contains(id);
    correos.deleteById(id);
  }

  @Test
  void unCorreoConSuProximoIntentoEnElFuturoNoSaleTodavia() {
    UUID id = encolar(AHORA.plus(Duration.ofMinutes(5)), 1);

    List<CorreoPendiente> enviables = repositorio.buscarEnviables(5, AHORA, 100);

    assertThat(enviables).extracting(CorreoPendiente::id).doesNotContain(id);
    correos.deleteById(id);
  }

  @Test
  void unCorreoQueLlegoAlTopeDeIntentosNoSaleNunca() {
    UUID id = encolar(AHORA, 5);

    List<CorreoPendiente> enviables = repositorio.buscarEnviables(5, AHORA, 100);

    assertThat(enviables).extracting(CorreoPendiente::id).doesNotContain(id);
    correos.deleteById(id);
  }

  @Test
  void unCorreoYaEnviadoNoSaleEnLosEnviables() {
    UUID id = encolar(AHORA, 0);
    repositorio.marcarEnviado(id, AHORA);

    List<CorreoPendiente> enviables = repositorio.buscarEnviables(5, AHORA, 100);

    assertThat(enviables).extracting(CorreoPendiente::id).doesNotContain(id);
    correos.deleteById(id);
  }

  @Test
  void elReclamoSubeLosIntentosYEmpujaElProximo() {
    UUID id = encolar(AHORA, 0);
    Instant proximo = AHORA.plus(Duration.ofMinutes(1));

    boolean ganado = repositorio.reclamar(id, AHORA, proximo);

    assertThat(ganado).isTrue();
    CorreoPendienteJpaEntity fila = correos.findById(id).orElseThrow();
    assertThat(fila.getIntentos()).isEqualTo(1);
    assertThat(fila.getProximoIntentoEn()).isEqualTo(proximo);
    correos.deleteById(id);
  }

  @Test
  void reclamarDosVecesSeguidasSoloLoGanaElPrimero() {
    UUID id = encolar(AHORA, 0);

    boolean primero = repositorio.reclamar(id, AHORA, AHORA.plus(Duration.ofMinutes(1)));
    boolean segundo = repositorio.reclamar(id, AHORA, AHORA.plus(Duration.ofMinutes(1)));

    assertThat(primero).isTrue();
    assertThat(segundo).isFalse();
    correos.deleteById(id);
  }

  /**
   * Dos instancias por el mismo correo, de verdad y en paralelo. Es el equivalente de la prueba de
   * concurrencia del inventario: con dos hilos arrancando a la vez sobre la misma fila, exactamente
   * uno tiene que ganar — si ganaran los dos, ese comprador recibe su comprobante por duplicado.
   */
  @Test
  void dosInstanciasALaVezSoloUnaGanaElReclamo() throws InterruptedException {
    UUID id = encolar(AHORA, 0);
    int hilos = 2;
    CountDownLatch salida = new CountDownLatch(1);
    CountDownLatch terminados = new CountDownLatch(hilos);
    AtomicInteger ganados = new AtomicInteger();

    try (ExecutorService ejecutor = Executors.newFixedThreadPool(hilos)) {
      for (int i = 0; i < hilos; i++) {
        ejecutor.submit(
            () -> {
              try {
                salida.await();
                if (repositorio.reclamar(id, AHORA, AHORA.plus(Duration.ofMinutes(1)))) {
                  ganados.incrementAndGet();
                }
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              } finally {
                terminados.countDown();
              }
            });
      }
      salida.countDown();
      assertThat(terminados.await(30, TimeUnit.SECONDS)).isTrue();
    }

    assertThat(ganados.get()).isEqualTo(1);
    correos.deleteById(id);
  }

  @Test
  void laPurgaBorraLosEnviadosViejosYRespetaLosRecientes() {
    UUID viejo = encolar(AHORA, 0);
    UUID reciente = encolar(AHORA, 0);
    UUID sinEnviar = encolar(AHORA, 0);
    repositorio.marcarEnviado(viejo, AHORA.minus(Duration.ofDays(31)));
    repositorio.marcarEnviado(reciente, AHORA.minus(Duration.ofDays(2)));

    int purgados = repositorio.purgarEnviados(AHORA.minus(Duration.ofDays(30)));

    assertThat(purgados).isEqualTo(1);
    assertThat(correos.findById(viejo)).isEmpty();
    assertThat(correos.findById(reciente)).isPresent();
    assertThat(correos.findById(sinEnviar)).isPresent();
    correos.deleteAllById(List.of(reciente, sinEnviar));
  }

  @Test
  void elFalloQuedaEscritoEnLaFila() {
    UUID id = encolar(AHORA, 0);

    repositorio.registrarFallo(id, "MailSendException: connection refused");

    assertThat(correos.findById(id).orElseThrow().getUltimoError())
        .isEqualTo("MailSendException: connection refused");
    correos.deleteById(id);
  }

  private UUID encolar(Instant proximoIntentoEn, int intentos) {
    UUID id = UUID.randomUUID();
    correos.save(
        new CorreoPendienteJpaEntity(
            id,
            "quien.compro@example.com",
            "Tu compra en Tecno Sport",
            "<p>Gracias por tu compra.</p>",
            AHORA,
            proximoIntentoEn,
            intentos,
            null,
            null));
    return id;
  }
}
