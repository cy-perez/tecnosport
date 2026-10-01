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

  /**
   * Presencia y orden, no el contenido exacto: desde {@code V72} la tabla ya no arranca vacía, y es
   * la cuarta vez que una prueba de repositorio se rompe por suponer lo contrario (ver la de
   * marcas).
   */
  @Test
  void listarTodasDevuelveLosAtributosOrdenadosPorNombreConSusValoresPermitidos() {
    atributos.save(
        new AtributoJpaEntity(
            UUID.randomUUID(), "Peso", "NUMERO", List.of("1", "2", "3"), Instant.now()));
    atributos.save(
        new AtributoJpaEntity(UUID.randomUUID(), "Acabado", "TEXTO", List.of(), Instant.now()));

    List<Atributo> resultado = repositorio.listarTodas();

    assertThat(resultado).extracting(Atributo::nombre).contains("Acabado", "Peso");
    assertThat(resultado).extracting(Atributo::nombre).isSorted();
    assertThat(resultado)
        .filteredOn(a -> a.nombre().equals("Peso"))
        .flatExtracting(Atributo::valoresPermitidos)
        .containsExactlyInAnyOrder("1", "2", "3");
  }

  /**
   * Lo que {@code V72} deja sembrado y {@code AprobarBorrador} busca por nombre en minúscula: sin
   * estas dos filas, aprobar un borrador con tonos o tallas responde {@code
   * ATRIBUTO_DE_CATALOGO_NO_DEFINIDO}.
   */
  @Test
  void laMigracionSiembraColorYTallaParaLaIngestaDeProveedores() {
    List<Atributo> resultado = repositorio.listarTodas();

    assertThat(resultado)
        .filteredOn(a -> a.nombre().equals("Color"))
        .singleElement()
        .satisfies(
            color -> {
              assertThat(color.tipo()).isEqualTo(TipoAtributo.COLOR);
              assertThat(color.valoresPermitidos()).isEmpty();
            });
    assertThat(resultado)
        .filteredOn(a -> a.nombre().equals("Talla"))
        .singleElement()
        .satisfies(
            talla -> {
              assertThat(talla.tipo()).isEqualTo(TipoAtributo.TEXTO);
              // V73: sin valores permitidos, o una XXL real no se podría aprobar.
              assertThat(talla.valoresPermitidos()).isEmpty();
            });
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

  @Test
  void laUnidadVuelveDeLaBase() {
    AtributoJpaEntity garantia =
        atributos.save(
            new AtributoJpaEntity(
                UUID.randomUUID(), "Garantía", "NUMERO", List.of(), Instant.now(), "meses"));

    Atributo encontrado = repositorio.buscarPorId(garantia.getId()).orElseThrow();

    assertThat(encontrado.unidad()).contains("meses");
  }
}
