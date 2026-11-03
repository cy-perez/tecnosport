package co.tecnosport.api.infrastructure.compartido;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Sin {@code @Transactional} en la clase, a diferencia de las vecinas: la bitácora confirma cada
 * sentencia por su cuenta ({@code REQUIRES_NEW}), así que una transacción de prueba alrededor no
 * revertiría nada y además escondería lo que importa —que la otra conexión vea el arriendo—. Cada
 * prueba usa un nombre propio para no pisarse.
 */
@SpringBootTest
@Testcontainers
class BitacoraDeTareasJpaTest {

  @Container @ServiceConnection
  static PostgreSQLContainer postgres =
      new PostgreSQLContainer(DockerImageName.parse("postgres:16"));

  private static final Duration INTERVALO = Duration.ofMinutes(15);
  private static final Duration ARRIENDO = Duration.ofMinutes(30);

  // Truncado a microsegundos: es lo que guarda un timestamptz, y la prueba compara contra la base.
  private static final Instant AHORA = Instant.now().truncatedTo(ChronoUnit.MICROS);

  @Autowired private BitacoraDeTareasJpa bitacora;
  @Autowired private JdbcTemplate jdbc;

  /** Vista por primera vez en {@code en}: la anota y no la toma. */
  private String vistaEn(Instant en) {
    String nombre = "prueba-" + UUID.randomUUID();
    assertThat(bitacora.tomar(nombre, INTERVALO, ARRIENDO, UUID.randomUUID(), en)).isFalse();
    return nombre;
  }

  private boolean tomar(String nombre, Duration intervalo, Instant ahora) {
    return bitacora.tomar(nombre, intervalo, ARRIENDO, UUID.randomUUID(), ahora);
  }

  @Test
  void laPrimeraVezSoloLaAnotaYCuentaElIntervaloDesdeAhi() {
    String nombre = vistaEn(AHORA);

    assertThat(instante(nombre, "tomada_en")).isEqualTo(AHORA);
    assertThat(instante(nombre, "en_curso_hasta")).isNull();
    assertThat(tomar(nombre, INTERVALO, AHORA.plusSeconds(1))).isFalse();
    assertThat(tomar(nombre, INTERVALO, AHORA.plus(INTERVALO))).isTrue();
  }

  @Test
  void tomadaNadieMasLaTomaMientrasDuraElArriendo() {
    String nombre = vistaEn(AHORA.minus(INTERVALO));
    UUID quien = UUID.randomUUID();

    boolean primera = bitacora.tomar(nombre, INTERVALO, ARRIENDO, quien, AHORA);
    boolean segunda = tomar(nombre, Duration.ZERO, AHORA.plus(ARRIENDO).minusSeconds(1));

    assertThat(primera).isTrue();
    assertThat(segunda).isFalse();
    assertThat(instante(nombre, "en_curso_hasta")).isEqualTo(AHORA.plus(ARRIENDO));
    assertThat(columna(nombre, "arrendada_por", UUID.class)).isEqualTo(quien);
  }

  /** El intervalo se cuenta desde que se tomó, no desde que terminó: terminar tarde no lo corre. */
  @Test
  void elIntervaloSeCuentaDesdeQueSeTomo() {
    String nombre = vistaEn(AHORA.minus(INTERVALO));
    UUID quien = UUID.randomUUID();
    bitacora.tomar(nombre, INTERVALO, ARRIENDO, quien, AHORA);
    bitacora.terminar(nombre, quien, AHORA.plusSeconds(40), null);

    boolean antes = tomar(nombre, INTERVALO, AHORA.plus(INTERVALO).minusSeconds(1));
    boolean justo = tomar(nombre, INTERVALO, AHORA.plus(INTERVALO));

    assertThat(antes).isFalse();
    assertThat(justo).isTrue();
  }

  @Test
  void unArriendoVencidoLoTomaOtro() {
    String nombre = vistaEn(AHORA.minus(INTERVALO));
    tomar(nombre, INTERVALO, AHORA);

    // Quien la tomó murió sin terminarla: nunca llamó a terminar.
    boolean antesDeVencer = tomar(nombre, INTERVALO, AHORA.plus(ARRIENDO).minusSeconds(1));
    boolean alVencer = tomar(nombre, INTERVALO, AHORA.plus(ARRIENDO));

    assertThat(antesDeVencer).isFalse();
    assertThat(alVencer).isTrue();
  }

  /**
   * La segunda revisión: un hilo congelado más allá de su petición que vuelve y termina no puede
   * soltar el arriendo de la vuelta que tomó la tarea después.
   */
  @Test
  void quienPerdioSuArriendoNoSueltaElDelQueLaTomoDespues() {
    String nombre = vistaEn(AHORA.minus(INTERVALO));
    UUID congelado = UUID.randomUUID();
    UUID actual = UUID.randomUUID();
    bitacora.tomar(nombre, INTERVALO, ARRIENDO, congelado, AHORA);
    Instant vencido = AHORA.plus(ARRIENDO);
    bitacora.tomar(nombre, INTERVALO, ARRIENDO, actual, vencido);

    bitacora.terminar(nombre, congelado, vencido.plusSeconds(5), null);

    assertThat(columna(nombre, "arrendada_por", UUID.class)).isEqualTo(actual);
    assertThat(instante(nombre, "en_curso_hasta")).isEqualTo(vencido.plus(ARRIENDO));
    assertThat(tomar(nombre, Duration.ZERO, vencido.plusSeconds(10))).isFalse();
  }

  @Test
  void terminarSueltaElArriendoYGuardaElFalloHastaLaVueltaBuena() {
    String nombre = vistaEn(AHORA.minus(INTERVALO));
    UUID quien = UUID.randomUUID();
    bitacora.tomar(nombre, INTERVALO, ARRIENDO, quien, AHORA);
    bitacora.terminar(nombre, quien, AHORA, "java.lang.IllegalStateException: Wompi no responde");

    assertThat(instante(nombre, "en_curso_hasta")).isNull();
    assertThat(columna(nombre, "arrendada_por", UUID.class)).isNull();
    assertThat(instante(nombre, "terminada_en")).isEqualTo(AHORA);
    assertThat(columna(nombre, "fallo", String.class)).contains("Wompi no responde");

    Instant despues = AHORA.plus(INTERVALO);
    UUID otra = UUID.randomUUID();
    bitacora.tomar(nombre, INTERVALO, ARRIENDO, otra, despues);
    bitacora.terminar(nombre, otra, despues, null);

    assertThat(columna(nombre, "fallo", String.class)).isNull();
  }

  @Test
  void dosVueltasALaVezNoTomanDosVecesLaMismaTarea() throws Exception {
    String nombre = vistaEn(AHORA.minus(INTERVALO));
    int hilos = 8;
    CountDownLatch salida = new CountDownLatch(1);
    ExecutorService ejecutor = Executors.newFixedThreadPool(hilos);
    try {
      List<Future<Boolean>> intentos = new ArrayList<>();
      for (int i = 0; i < hilos; i++) {
        intentos.add(
            ejecutor.submit(
                () -> {
                  salida.await();
                  return tomar(nombre, INTERVALO, AHORA);
                }));
      }
      salida.countDown();

      int tomadas = 0;
      for (Future<Boolean> intento : intentos) {
        if (intento.get(30, TimeUnit.SECONDS)) {
          tomadas++;
        }
      }
      assertThat(tomadas).isEqualTo(1);
    } finally {
      ejecutor.shutdownNow();
    }
  }

  /** Y la primera vez también: ocho vueltas que la ven a la vez no la toman, solo la anotan. */
  @Test
  void dosVueltasQueLaVenPorPrimeraVezNoLaToman() throws Exception {
    String nombre = "prueba-" + UUID.randomUUID();
    ExecutorService ejecutor = Executors.newFixedThreadPool(8);
    try {
      CountDownLatch salida = new CountDownLatch(1);
      List<Future<Boolean>> intentos = new ArrayList<>();
      for (int i = 0; i < 8; i++) {
        intentos.add(
            ejecutor.submit(
                () -> {
                  salida.await();
                  return tomar(nombre, Duration.ofHours(1), AHORA);
                }));
      }
      salida.countDown();
      for (Future<Boolean> intento : intentos) {
        assertThat(intento.get(30, TimeUnit.SECONDS)).isFalse();
      }
    } finally {
      ejecutor.shutdownNow();
    }
  }

  private Instant instante(String nombre, String columna) {
    OffsetDateTime valor = columna(nombre, columna, OffsetDateTime.class);
    return valor == null ? null : valor.toInstant();
  }

  private <T> T columna(String nombre, String columna, Class<T> tipo) {
    return jdbc.queryForObject(
        "select " + columna + " from ejecucion_de_tarea where nombre = ?", tipo, nombre);
  }
}
