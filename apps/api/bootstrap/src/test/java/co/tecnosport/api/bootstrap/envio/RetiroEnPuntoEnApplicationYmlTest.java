package co.tecnosport.api.bootstrap.envio;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;

/**
 * La recogida en el punto arranca apagada en todo ambiente que no la encienda explícito. Los textos
 * publicados ya no la prometen, así que un valor por omisión en {@code true} la volvería a ofrecer
 * sin que ningún documento la respalde.
 */
class RetiroEnPuntoEnApplicationYmlTest {

  @Test
  void laRecogidaArrancaApagada() throws IOException {
    Object valor =
        new YamlPropertySourceLoader()
            .load("application.yml", new ClassPathResource("application.yml"))
            .get(0)
            .getProperty("tecnosport.retiro-en-punto.habilitado");

    assertEquals("${RETIRO_EN_PUNTO_HABILITADO:false}", String.valueOf(valor));
  }
}
