package co.tecnosport.api.infrastructure.catalogo;

import static org.assertj.core.api.Assertions.assertThat;

import co.tecnosport.api.domain.catalogo.Categoria;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.infrastructure.catalogo.entidad.CategoriaJpaEntity;
import java.time.Instant;
import java.util.List;
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
class RepositorioCategoriasJpaTest {

  @Container @ServiceConnection
  static PostgreSQLContainer postgres =
      new PostgreSQLContainer(DockerImageName.parse("postgres:16"));

  @Autowired private RepositorioCategoriasJpa repositorio;
  @Autowired private CategoriaJpaRepository categorias;

  @Test
  void listarTodasDevuelveLasCategoriasOrdenadasPorNombre() {
    categorias.save(
        new CategoriaJpaEntity(
            UUID.randomUUID(), "Celulares", "celulares-tc1", "CELULARES", Instant.now()));
    categorias.save(
        new CategoriaJpaEntity(UUID.randomUUID(), "Bolsos", "bolsos-tc1", "BOLSOS", Instant.now()));

    List<Categoria> resultado = repositorio.listarTodas();

    assertThat(resultado).extracting(Categoria::nombre).containsExactly("Bolsos", "Celulares");
    assertThat(resultado)
        .extracting(Categoria::linea)
        .contains(LineaCatalogo.BOLSOS, LineaCatalogo.CELULARES);
  }
}
