package co.tecnosport.api.infrastructure.catalogo;

import static org.assertj.core.api.Assertions.assertThat;

import co.tecnosport.api.domain.catalogo.ColorDePaleta;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** V75 siembra la paleta; el repositorio la devuelve en su orden y cada HEX es válido. */
@SpringBootTest
@Testcontainers
@Transactional
class RepositorioPaletaDeColoresJpaTest {

  @Container @ServiceConnection
  static PostgreSQLContainer postgres =
      new PostgreSQLContainer(DockerImageName.parse("postgres:16"));

  @Autowired private RepositorioPaletaDeColoresJpa repositorio;

  @Test
  void laPaletaLlegaEnSuOrdenConNombreEnInglesYHex() {
    List<ColorDePaleta> paleta = repositorio.listarTodos();

    assertThat(paleta).hasSize(24);
    assertThat(paleta.get(0).nombre()).isEqualTo("Negro");
    assertThat(paleta.get(0).nombreEn()).isEqualTo("Black");
    assertThat(paleta).extracting(ColorDePaleta::orden).isSorted();
    assertThat(paleta).allMatch(c -> c.hex().matches("^#[0-9A-F]{6}$"));
  }
}
