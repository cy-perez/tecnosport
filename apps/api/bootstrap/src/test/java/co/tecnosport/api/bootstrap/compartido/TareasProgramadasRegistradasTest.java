package co.tecnosport.api.bootstrap.compartido;

import static org.assertj.core.api.Assertions.assertThat;

import co.tecnosport.api.application.compartido.TareaProgramada;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Las 17 tareas que corre el tick, por su nombre en la bitácora (ADR-0104).
 *
 * <p>Dos cosas que ninguna otra prueba ve. Que una tarea que existía siga registrada: si pierde el
 * {@code @Bean} del catálogo, deja de correr sin un solo error. Y que ninguna cambie de nombre: el
 * nombre es la llave de {@code ejecucion_de_tarea}, y una renombrada nace sin historial y corre en
 * el tick siguiente aunque acabara de correr. Si el cambio es a propósito, se cambia aquí.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles({"local", "e2e"})
class TareasProgramadasRegistradasTest {

  @Container @ServiceConnection
  static PostgreSQLContainer postgres =
      new PostgreSQLContainer(DockerImageName.parse("postgres:16"));

  @Autowired private List<TareaProgramada> tareas;

  /** Y en el orden del catálogo: primero lo que mueve dinero y reservas, al final las purgas. */
  @Test
  void elTickConoceLasDiecisieteTareasPorSuNombreYEnOrden() {
    assertThat(tareas)
        .extracting(TareaProgramada::nombre)
        .containsExactly(
            "conciliacion-wompi",
            "conciliacion-sistecredito",
            "resolucion-emisiones",
            "vencer-contraentregas",
            "vencer-recogidas",
            "conciliacion-envios",
            "comprobantes-de-compra",
            "bandeja-de-salida",
            "ingestas-abandonadas",
            "aviso-plazo-de-entrega",
            "aviso-revision-envios",
            "vigilancia-saldo-envios",
            "vigilancia-sobrecostos-envios",
            "expiracion-disponibilidad",
            "purga-idempotencia",
            "purga-carritos",
            "purga-desafios-segundo-factor");
  }

  @Test
  void ningunaTieneUnIntervaloNuloOVacio() {
    assertThat(tareas).allSatisfy(t -> assertThat(t.intervalo()).isPositive());
  }
}
