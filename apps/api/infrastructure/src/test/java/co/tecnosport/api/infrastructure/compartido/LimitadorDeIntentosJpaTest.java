package co.tecnosport.api.infrastructure.compartido;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@Testcontainers
@Transactional
class LimitadorDeIntentosJpaTest {

  @Container @ServiceConnection
  static PostgreSQLContainer postgres =
      new PostgreSQLContainer(DockerImageName.parse("postgres:16"));

  @Autowired private LimitadorDeIntentosJpa limitador;
  @Autowired private PlatformTransactionManager transactionManager;

  private static final Duration VENTANA = Duration.ofMinutes(15);

  private String claveNueva() {
    return "prueba:" + UUID.randomUUID();
  }

  @Test
  void permiteHastaElMaximoDentroDeLaVentana() {
    String clave = claveNueva();
    Instant ahora = Instant.now();

    for (int i = 0; i < 3; i++) {
      assertThat(limitador.permitir(clave, 3, VENTANA, ahora)).isTrue();
    }
  }

  @Test
  void rechazaElIntentoQueSuperaElMaximo() {
    String clave = claveNueva();
    Instant ahora = Instant.now();
    for (int i = 0; i < 3; i++) {
      limitador.permitir(clave, 3, VENTANA, ahora);
    }

    assertThat(limitador.permitir(clave, 3, VENTANA, ahora)).isFalse();
  }

  @Test
  void permiteDeNuevoTrasVencerLaVentana() {
    String clave = claveNueva();
    Instant ahora = Instant.now();
    for (int i = 0; i < 3; i++) {
      limitador.permitir(clave, 3, VENTANA, ahora);
    }
    assertThat(limitador.permitir(clave, 3, VENTANA, ahora)).isFalse();

    boolean permitidoTrasVencer =
        limitador.permitir(clave, 3, VENTANA, ahora.plus(VENTANA).plusSeconds(1));

    assertThat(permitidoTrasVencer).isTrue();
  }

  @Test
  void elContadorPersisteAunSiLaTransaccionDeNegocioQueLoLlamaRevierte() {
    // Reproduce el caso real: un caso de uso llama permitir() y luego lanza una excepción de
    // negocio (clave incorrecta, correo ya registrado...) dentro de la misma transacción que
    // abrió el controlador. Si permitir() compartiera esa transacción, el incremento se revertiría
    // junto con todo lo demás — exactamente los intentos que este mecanismo existe para contar.
    String clave = claveNueva();
    Instant ahora = Instant.now();
    TransactionTemplate transaccionDeNegocio = new TransactionTemplate(transactionManager);

    assertThrows(
        IllegalStateException.class,
        () ->
            transaccionDeNegocio.executeWithoutResult(
                estado -> {
                  limitador.permitir(clave, 1, VENTANA, ahora);
                  throw new IllegalStateException("simula, p. ej., credenciales inválidas");
                }));

    // Con máximo 1, el intento de arriba ya debió consumir el único cupo — si permitir() hubiera
    // revertido con la transacción de negocio, este segundo también sería true.
    assertThat(limitador.permitir(clave, 1, VENTANA, ahora)).isFalse();
  }

  @Test
  void bajoConcurrenciaRealSoloDejaPasarExactamenteElMaximo() throws Exception {
    // Antes de la sentencia atómica, un lost-update aquí podía dejar pasar bastante más que el
    // máximo bajo concurrencia sostenida (ver LimitadorDeIntentosJpa) — este test no pasaría con
    // la implementación de lectura-y-escritura separada de antes.
    String clave = claveNueva();
    Instant ahora = Instant.now();
    int maximo = 10;
    int hilos = 20;

    ExecutorService pool = Executors.newFixedThreadPool(hilos);
    CountDownLatch salida = new CountDownLatch(1);
    List<Callable<Boolean>> tareas = new ArrayList<>();
    for (int i = 0; i < hilos; i++) {
      tareas.add(
          () -> {
            salida.await();
            return limitador.permitir(clave, maximo, VENTANA, ahora);
          });
    }

    List<Future<Boolean>> resultados = new ArrayList<>();
    for (Callable<Boolean> tarea : tareas) {
      resultados.add(pool.submit(tarea));
    }
    salida.countDown();

    long permitidos = 0;
    for (Future<Boolean> resultado : resultados) {
      if (resultado.get(20, TimeUnit.SECONDS)) {
        permitidos++;
      }
    }
    pool.shutdown();

    assertThat(permitidos).isEqualTo(maximo);
  }

  @Test
  void clavesDistintasNoComparganPresupuesto() {
    Instant ahora = Instant.now();
    String claveA = claveNueva();
    String claveB = claveNueva();
    limitador.permitir(claveA, 1, VENTANA, ahora);

    assertThat(limitador.permitir(claveA, 1, VENTANA, ahora)).isFalse();
    assertThat(limitador.permitir(claveB, 1, VENTANA, ahora)).isTrue();
  }
}
