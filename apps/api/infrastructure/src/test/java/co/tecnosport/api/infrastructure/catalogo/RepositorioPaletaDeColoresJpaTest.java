package co.tecnosport.api.infrastructure.catalogo;

import static org.assertj.core.api.Assertions.assertThat;

import co.tecnosport.api.domain.catalogo.ColorDePaleta;
import java.text.Collator;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** La paleta que siembran V75 y V78, contra Postgres real: completa y en orden alfabético. */
@SpringBootTest
@Testcontainers
@Transactional
class RepositorioPaletaDeColoresJpaTest {

  @Container @ServiceConnection
  static PostgreSQLContainer postgres =
      new PostgreSQLContainer(DockerImageName.parse("postgres:16"));

  @Autowired private RepositorioPaletaDeColoresJpa paleta;

  @Test
  void laPaletaAmpliadaLlegaEnOrdenAlfabeticoDelNombreEnEspanol() {
    List<String> nombres = paleta.listarTodos().stream().map(ColorDePaleta::nombre).toList();

    Collator espanol = Collator.getInstance(Locale.forLanguageTag("es"));
    espanol.setStrength(Collator.PRIMARY);
    assertThat(nombres).isSortedAccordingTo(espanol);
    assertThat(nombres).hasSize(63).doesNotHaveDuplicates();
    assertThat(nombres)
        .contains("Negro", "Verde militar", "Azul oscuro", "Gris jaspe", "Coñac", "Índigo");
  }
}
