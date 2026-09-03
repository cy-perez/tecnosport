package co.tecnosport.api.infrastructure.envio;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@Testcontainers
@Transactional
class RepositorioCoberturaContraentregaJpaTest {

  @Container @ServiceConnection
  static PostgreSQLContainer postgres =
      new PostgreSQLContainer(DockerImageName.parse("postgres:16"));

  @Autowired private RepositorioCoberturaContraentregaJpa repositorio;

  @Test
  void unaCiudadNoCubiertaPorDefecto() {
    assertThat(repositorio.estaCubierta("05001")).isFalse();
  }

  @Test
  void agregarCubreLaCiudad() {
    repositorio.agregar("05001");

    assertThat(repositorio.estaCubierta("05001")).isTrue();
  }

  @Test
  void agregarDosVecesLaMismaCiudadNoFalla() {
    repositorio.agregar("05001");
    repositorio.agregar("05001");

    assertThat(repositorio.estaCubierta("05001")).isTrue();
  }

  @Test
  void quitarUnaCiudadCubiertaLaExcluye() {
    repositorio.agregar("05001");

    repositorio.quitar("05001");

    assertThat(repositorio.estaCubierta("05001")).isFalse();
  }

  @Test
  void quitarUnaCiudadNoCubiertaNoFalla() {
    repositorio.quitar("05001");

    assertThat(repositorio.estaCubierta("05001")).isFalse();
  }

  @Test
  void listarDevuelveTodasLasCiudadesCubiertas() {
    repositorio.agregar("05001");
    repositorio.agregar("11001");

    assertThat(repositorio.listar()).containsExactlyInAnyOrder("05001", "11001");
  }
}
