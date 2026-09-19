package co.tecnosport.api.infrastructure.catalogo;

import static org.assertj.core.api.Assertions.assertThat;

import co.tecnosport.api.domain.catalogo.Marca;
import co.tecnosport.api.infrastructure.catalogo.entidad.CategoriaJpaEntity;
import co.tecnosport.api.infrastructure.catalogo.entidad.MarcaJpaEntity;
import co.tecnosport.api.infrastructure.catalogo.entidad.ProductoJpaEntity;
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

  private static final Instant AHORA = Instant.parse("2026-09-19T15:00:00Z");

  @Autowired private RepositorioMarcasJpa repositorio;
  @Autowired private MarcaJpaRepository marcas;
  @Autowired private CategoriaJpaRepository categorias;
  @Autowired private ProductoJpaRepository productos;

  @Test
  void listarTodasDevuelveLasMarcasOrdenadasPorNombre() {
    marcas.save(new MarcaJpaEntity(UUID.randomUUID(), "Zeta Sport", Instant.now()));
    marcas.save(new MarcaJpaEntity(UUID.randomUUID(), "Andes Wear", Instant.now()));

    List<Marca> resultado = repositorio.listarTodas();

    assertThat(resultado).extracting(Marca::nombre).containsExactly("Andes Wear", "Zeta Sport");
  }

  /**
   * Las tres situaciones que importan, en una sola prueba porque la gracia está en compararlas: una
   * marca con producto publicado, una con producto en borrador y una sin nada. Solo la primera es
   * un filtro honesto — las otras dos llevan a una rejilla vacía, que es justo lo que la vitrina
   * llevaba haciendo.
   */
  @Test
  void listarConProductosPublicadosIgnoraLasVaciasYLasQueSoloTienenBorradores() {
    CategoriaJpaEntity categoria =
        categorias.save(
            new CategoriaJpaEntity(
                UUID.randomUUID(), "Celulares TC", "celulares-tc-marcas", "TECNOLOGIA", AHORA));
    MarcaJpaEntity conPublicado =
        marcas.save(new MarcaJpaEntity(UUID.randomUUID(), "Con publicado", AHORA));
    MarcaJpaEntity soloBorrador =
        marcas.save(new MarcaJpaEntity(UUID.randomUUID(), "Solo borrador", AHORA));
    marcas.save(new MarcaJpaEntity(UUID.randomUUID(), "Sin nada", AHORA));

    guardarProducto(conPublicado.getId(), categoria.getId(), "PUBLICADO", "pub-marca");
    guardarProducto(soloBorrador.getId(), categoria.getId(), "BORRADOR", "bor-marca");

    List<Marca> resultado = repositorio.listarConProductosPublicados();

    assertThat(resultado).extracting(Marca::nombre).containsExactly("Con publicado");
  }

  /**
   * Dos productos publicados de la misma marca no la duplican: es un {@code exists}, no un join.
   */
  @Test
  void listarConProductosPublicadosNoRepiteLaMarcaConVariosProductos() {
    CategoriaJpaEntity categoria =
        categorias.save(
            new CategoriaJpaEntity(
                UUID.randomUUID(), "Celulares TC2", "celulares-tc2-marcas", "TECNOLOGIA", AHORA));
    MarcaJpaEntity marca = marcas.save(new MarcaJpaEntity(UUID.randomUUID(), "Repetida", AHORA));

    guardarProducto(marca.getId(), categoria.getId(), "PUBLICADO", "rep-uno");
    guardarProducto(marca.getId(), categoria.getId(), "PUBLICADO", "rep-dos");

    assertThat(repositorio.listarConProductosPublicados()).hasSize(1);
  }

  private void guardarProducto(UUID marcaId, UUID categoriaId, String estado, String slug) {
    productos.save(
        new ProductoJpaEntity(
            UUID.randomUUID(),
            slug,
            slug,
            "Descripción",
            marcaId,
            categoriaId,
            estado,
            AHORA,
            AHORA));
  }

  @Test
  void buscarPorIdDevuelveVacioSiNoExiste() {
    Optional<Marca> resultado = repositorio.buscarPorId(UUID.randomUUID());

    assertThat(resultado).isEmpty();
  }

  @Test
  void buscarPorIdDevuelveLaMarca() {
    MarcaJpaEntity guardada =
        marcas.save(new MarcaJpaEntity(UUID.randomUUID(), "Andes Wear", Instant.now()));

    Optional<Marca> resultado = repositorio.buscarPorId(guardada.getId());

    assertThat(resultado).isPresent();
    assertThat(resultado.orElseThrow().nombre()).isEqualTo("Andes Wear");
  }
}
