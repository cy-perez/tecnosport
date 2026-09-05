package co.tecnosport.api.infrastructure.catalogo;

import static org.assertj.core.api.Assertions.assertThat;

import co.tecnosport.api.domain.catalogo.Marca;
import co.tecnosport.api.infrastructure.catalogo.entidad.MarcaJpaEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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
class RepositorioMarcasJpaTest {

  @Container @ServiceConnection
  static PostgreSQLContainer postgres =
      new PostgreSQLContainer(DockerImageName.parse("postgres:16"));

  @Autowired private RepositorioMarcasJpa repositorio;
  @Autowired private MarcaJpaRepository marcas;

  @Test
  void listarTodasDevuelveLasMarcasOrdenadasPorNombre() {
    marcas.save(new MarcaJpaEntity(UUID.randomUUID(), "Zeta Sport", Instant.now()));
    marcas.save(new MarcaJpaEntity(UUID.randomUUID(), "Andes Wear", Instant.now()));

    List<Marca> resultado = repositorio.listarTodas();

    assertThat(resultado).extracting(Marca::nombre).containsExactly("Andes Wear", "Zeta Sport");
  }

  @Test
  void buscarPorIdDevuelveVacioSiNoExiste() {
    Optional<Marca> resultado = repositorio.buscarPorId(UUID.randomUUID());

    assertThat(resultado).isEmpty();
  }

  @Test
  void buscarPorIdDevuelveLaMarca() {
    MarcaJpaEntity guardada = marcas.save(new MarcaJpaEntity(UUID.randomUUID(), "Andes Wear", Instant.now()));

    Optional<Marca> resultado = repositorio.buscarPorId(guardada.getId());

    assertThat(resultado).isPresent();
    assertThat(resultado.orElseThrow().nombre()).isEqualTo("Andes Wear");
  }
}
