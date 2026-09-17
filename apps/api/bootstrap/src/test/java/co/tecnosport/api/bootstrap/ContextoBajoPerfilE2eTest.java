package co.tecnosport.api.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import co.tecnosport.api.application.envio.ConsultorDeSeguimiento;
import co.tecnosport.api.application.envio.CotizadorEnvio;
import co.tecnosport.api.application.envio.EmisorDeGuias;
import co.tecnosport.api.infrastructure.envio.siembra.CotizadorEnvioSembrado;
import co.tecnosport.api.infrastructure.envio.siembra.EmisorDeGuiasSembrado;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * La aplicación entera arranca bajo los perfiles que usa el flujo {@code recorridos}.
 *
 * <p><strong>Esta prueba existe por un fallo que costó un merge.</strong> La emisión de la guía
 * (adr/0033) le agregó a {@code SkydropxClient} un tercer puerto, {@link EmisorDeGuias}, y tres
 * beans que lo exigen sin mirar el perfil. Bajo {@code e2e} ese cliente no se registra y sus
 * puertos se sustituyen uno por uno; el tercero no se sustituyó, el contexto no refrescó, y {@code
 * gradlew build} pasó en verde porque <strong>ninguna prueba cargaba el contexto con ese
 * perfil</strong>. El único guardián era el flujo de integración continua, seis minutos después del
 * merge y ya sobre {@code main}.
 *
 * <p>Lo que impide, en concreto: que un cuarto puerto de {@code SkydropxClient} llegue sin su
 * sustituto. No hace falta que nadie se acuerde de esta regla — el contexto no refresca, y aquí
 * falla en la máquina de quien lo escribe.
 *
 * <p><strong>Los dos perfiles juntos, y no solo {@code e2e}</strong>: es la combinación que {@code
 * bootRun -Pperfiles=local,e2e} levanta en el flujo. {@code local} enciende además el sembrador del
 * catálogo, así que esta prueba cubre también que la siembra corra contra el esquema migrado.
 * Probar {@code e2e} a solas dejaría sin vigilar justo lo que se despliega en el recorrido.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles({"local", "e2e"})
class ContextoBajoPerfilE2eTest {

  @Container @ServiceConnection
  static PostgreSQLContainer postgres =
      new PostgreSQLContainer(DockerImageName.parse("postgres:16"));

  @Autowired private ApplicationContext contexto;

  @Test
  void elContextoArrancaConLosPerfilesDelRecorrido() {
    assertThat(contexto.getEnvironment().getActiveProfiles())
        .containsExactlyInAnyOrder("local", "e2e");
  }

  /**
   * Los tres puertos de {@code SkydropxClient}, servidos por sus dobles. Se afirma el tipo concreto
   * a propósito: que el bean exista no basta, porque el que no debe existir bajo este perfil es
   * justamente el cliente real, y pedirle credenciales a un despliegue que no las tiene es el
   * escenario que este perfil evita.
   */
  @Test
  void bajoE2eLosTresPuertosDeSkydropxLosSirvenLosDobles() {
    assertThat(contexto.getBean(CotizadorEnvio.class)).isInstanceOf(CotizadorEnvioSembrado.class);
    assertThat(contexto.getBean(EmisorDeGuias.class)).isInstanceOf(EmisorDeGuiasSembrado.class);
    assertThat(contexto.getBeanNamesForType(ConsultorDeSeguimiento.class)).hasSize(1);
  }
}
