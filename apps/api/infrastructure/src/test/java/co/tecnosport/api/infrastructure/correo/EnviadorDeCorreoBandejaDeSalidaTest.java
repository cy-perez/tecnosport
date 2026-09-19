package co.tecnosport.api.infrastructure.correo;

import static org.assertj.core.api.Assertions.assertThat;

import co.tecnosport.api.application.compartido.EnviadorDeCorreo;
import co.tecnosport.api.domain.compartido.CorreoElectronico;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * La prueba que justifica el mecanismo entero, y la única que no se puede escribir con dobles.
 *
 * <p><b>Sin {@code @Transactional} de clase, y aquí es el asunto y no un detalle:</b> lo que hay
 * que observar es qué queda comprometido cuando la transacción de quien llama revierte, y una
 * transacción de prueba envolviéndolo todo hace justamente imposible esa observación. Es la misma
 * razón por la que {@code RepositorioEmisionesJpaGuardarTest} vive en una clase aparte.
 *
 * <p>Lo que fija: encolar <b>se une</b> a la transacción de quien llama. Si esa transacción
 * revierte, el correo no existió nunca. Ese era el caso que {@code adr/0044} dejó abierto —el
 * comprador leyendo "reintegramos el dinero de tu pedido" sobre un reintegro sin constancia— y el
 * que se rompe en silencio el día que alguien le ponga {@code REQUIRES_NEW} a este adaptador
 * creyendo que lo mejora.
 */
@SpringBootTest
@Testcontainers
class EnviadorDeCorreoBandejaDeSalidaTest {

  @Container @ServiceConnection
  static PostgreSQLContainer postgres =
      new PostgreSQLContainer(DockerImageName.parse("postgres:16"));

  private static final Instant AHORA = Instant.parse("2026-09-19T12:00:00Z");
  private static final CorreoElectronico DESTINATARIO =
      new CorreoElectronico("quien.compro@example.com");

  @Autowired private CorreoPendienteJpaRepository correos;
  @Autowired private PlatformTransactionManager transactionManager;

  @AfterEach
  void limpiar() {
    correos.deleteAll();
  }

  @Test
  void loEncoladoDentroDeUnaTransaccionQueRevierteNoQuedaEnLaBandeja() {
    EnviadorDeCorreo enviador = new EnviadorDeCorreoBandejaDeSalida(correos, () -> AHORA);
    TransactionTemplate transaccion = new TransactionTemplate(transactionManager);

    transaccion.execute(
        estado -> {
          enviador.enviar(DESTINATARIO, "Reintegro de tu pedido", "<p>Reintegramos el dinero.</p>");
          estado.setRollbackOnly();
          return null;
        });

    assertThat(correos.findAll())
        .as("un correo de una operación que no ocurrió no se puede mandar")
        .isEmpty();
  }

  @Test
  void loEncoladoDentroDeUnaTransaccionQueConfirmaSiQuedaEnLaBandeja() {
    EnviadorDeCorreo enviador = new EnviadorDeCorreoBandejaDeSalida(correos, () -> AHORA);
    TransactionTemplate transaccion = new TransactionTemplate(transactionManager);

    transaccion.execute(
        estado -> {
          enviador.enviar(DESTINATARIO, "Reintegro de tu pedido", "<p>Reintegramos el dinero.</p>");
          return null;
        });

    assertThat(correos.findAll())
        .singleElement()
        .satisfies(
            fila -> {
              assertThat(fila.getDestinatario()).isEqualTo(DESTINATARIO.valor());
              assertThat(fila.getEnviadoEn()).isNull();
              assertThat(fila.getIntentos()).isZero();
              assertThat(fila.getProximoIntentoEn())
                  .as("el primer intento es inmediato: el espaciado solo cuenta cuando algo falla")
                  .isEqualTo(AHORA);
            });
  }

  /**
   * Las tareas no abren transacción a propósito, así que aquí la escritura se compromete sola. No
   * se gana atomicidad con el reclamo —eso lo siguen cubriendo el {@code reclamar}/{@code liberar}
   * de cada tarea— pero sí lo que la bandeja da a todos: el correo queda escrito y se reintenta.
   */
  @Test
  void encolarSinTransaccionAbiertaTambienFunciona() {
    EnviadorDeCorreo enviador = new EnviadorDeCorreoBandejaDeSalida(correos, () -> AHORA);

    enviador.enviar(DESTINATARIO, "Plazo de entrega vencido", "<p>Tu pedido se demoró.</p>");

    assertThat(correos.findAll()).hasSize(1);
  }
}
