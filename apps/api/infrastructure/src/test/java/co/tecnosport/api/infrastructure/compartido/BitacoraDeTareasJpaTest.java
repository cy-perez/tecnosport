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

  private static String nombreNuevo() {
    return "prueba-" + UUID.randomUUID();
  }

  @Test
  void laPrimeraVezLaTomaYMientrasDuraElArriendoNadieMas() {
    String nombre = nombreNuevo();

    boolean primera = bitacora.tomar(nombre, INTERVALO, ARRIENDO, AHORA);
    boolean segunda = bitacora.tomar(nombre, INTERVALO, ARRIENDO, AHORA.plusSeconds(60));

    assertThat(primera).isTrue();
    assertThat(segunda).isFalse();
    assertThat(instante(nombre, "en_curso_hasta")).isEqualTo(AHORA.plus(ARRIENDO));
  }

  @Test
  void terminadaNoLeTocaHastaQuePasaSuIntervalo() {
    String nombre = nombreNuevo();
    bitacora.tomar(nombre, INTERVALO, ARRIENDO, AHORA);
    bitacora.terminar(nombre, AHORA, null);

    boolean antes =
        bitacora.tomar(nombre, INTERVALO, ARRIENDO, AHORA.plus(INTERVALO).minusSeconds(1));
    boolean justo = bitacora.tomar(nombre, INTERVALO, ARRIENDO, AHORA.plus(INTERVALO));

    assertThat(antes).isFalse();
    assertThat(justo).isTrue();
  }

  @Test
  void unArriendoVencidoLoTomaOtro() {
    String nombre = nombreNuevo();
    bitacora.tomar(nombre, INTERVALO, ARRIENDO, AHORA);

    // Quien la tomó murió sin terminarla: nunca llamó a terminar.
    boolean antesDeVencer =
        bitacora.tomar(nombre, INTERVALO, ARRIENDO, AHORA.plus(ARRIENDO).minusSeconds(1));
    boolean alVencer = bitacora.tomar(nombre, INTERVALO, ARRIENDO, AHORA.plus(ARRIENDO));

    assertThat(antesDeVencer).isFalse();
    assertThat(alVencer).isTrue();
  }

  @Test
  void terminarSueltaElArriendoYGuardaElFalloHastaLaVueltaBuena() {
    String nombre = nombreNuevo();
    bitacora.tomar(nombre, INTERVALO, ARRIENDO, AHORA);
    bitacora.terminar(nombre, AHORA, "java.lang.IllegalStateException: Wompi no responde");

    assertThat(instante(nombre, "en_curso_hasta")).isNull();
    assertThat(instante(nombre, "terminada_en")).isEqualTo(AHORA);
    assertThat(columna(nombre, "fallo", String.class)).contains("Wompi no responde");

    Instant despues = AHORA.plus(INTERVALO);
    bitacora.tomar(nombre, INTERVALO, ARRIENDO, despues);
    bitacora.terminar(nombre, despues, null);

    assertThat(columna(nombre, "fallo", String.class)).isNull();
  }

  @Test
  void dosTicksALaVezNoTomanDosVecesLaMismaTarea() throws Exception {
    String nombre = nombreNuevo();
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
                  return bitacora.tomar(nombre, INTERVALO, ARRIENDO, AHORA);
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

  private Instant instante(String nombre, String columna) {
    OffsetDateTime valor = columna(nombre, columna, OffsetDateTime.class);
    return valor == null ? null : valor.toInstant();
  }

  private <T> T columna(String nombre, String columna, Class<T> tipo) {
    return jdbc.queryForObject(
        "select " + columna + " from ejecucion_de_tarea where nombre = ?", tipo, nombre);
  }
}
