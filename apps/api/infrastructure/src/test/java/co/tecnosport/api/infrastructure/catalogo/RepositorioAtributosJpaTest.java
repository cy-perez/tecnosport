package co.tecnosport.api.infrastructure.catalogo;

import static org.assertj.core.api.Assertions.assertThat;

import co.tecnosport.api.domain.catalogo.Atributo;
import co.tecnosport.api.domain.catalogo.TipoAtributo;
import co.tecnosport.api.infrastructure.catalogo.entidad.AtributoJpaEntity;
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
class RepositorioAtributosJpaTest {

  @Container @ServiceConnection
  static PostgreSQLContainer postgres =
      new PostgreSQLContainer(DockerImageName.parse("postgres:16"));

  @Autowired private RepositorioAtributosJpa repositorio;
  @Autowired private AtributoJpaRepository atributos;

  @Test
  void listarTodasDevuelveLosAtributosOrdenadosPorNombreConSusValoresPermitidos() {
    atributos.save(
        new AtributoJpaEntity(
            UUID.randomUUID(), "Talla", "TEXTO", List.of("S", "M", "L"), Instant.now()));
    atributos.save(
        new AtributoJpaEntity(UUID.randomUUID(), "Color", "COLOR", List.of(), Instant.now()));

    List<Atributo> resultado = repositorio.listarTodas();

    assertThat(resultado).extracting(Atributo::nombre).containsExactly("Color", "Talla");
    assertThat(resultado)
        .filteredOn(a -> a.nombre().equals("Talla"))
        .flatExtracting(Atributo::valoresPermitidos)
        .containsExactlyInAnyOrder("S", "M", "L");
  }

  @Test
  void buscarPorIdDevuelveVacioSiNoExiste() {
    assertThat(repositorio.buscarPorId(UUID.randomUUID())).isEmpty();
  }

  @Test
  void buscarPorIdDevuelveElAtributo() {
    AtributoJpaEntity guardado =
        atributos.save(
            new AtributoJpaEntity(UUID.randomUUID(), "Color", "COLOR", List.of(), Instant.now()));

    Optional<Atributo> resultado = repositorio.buscarPorId(guardado.getId());

    assertThat(resultado).isPresent();
    assertThat(resultado.orElseThrow().tipo()).isEqualTo(TipoAtributo.COLOR);
  }
}
