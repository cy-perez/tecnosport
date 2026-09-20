package co.tecnosport.api.infrastructure.catalogo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import org.springframework.dao.DataIntegrityViolationException;
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

  /**
   * Afirma el <b>orden</b> y la <b>presencia</b>, no el contenido exacto de la tabla, por la misma
   * razón por la que la prueba equivalente de categorías tuvo que dejar de hacerlo: usaba {@code
   * containsExactly} con las dos filas que ella misma crea, y eso da por hecho que la tabla arranca
   * vacía. Dejó de ser cierto con {@code V54__marcas_reales.sql}, que da de alta las doce marcas
   * reales del negocio.
   *
   * <p>Van tres veces que una prueba de repositorio se rompe por suponer una tabla vacía. Lo que
   * tiene que demostrar es que {@code listarTodas} devuelve lo que hay, ordenado por nombre.
   */
  @Test
  void listarTodasDevuelveLasMarcasOrdenadasPorNombre() {
    marcas.save(new MarcaJpaEntity(UUID.randomUUID(), "Zeta Sport", Instant.now()));
    marcas.save(new MarcaJpaEntity(UUID.randomUUID(), "Andes Wear", Instant.now()));

    List<Marca> resultado = repositorio.listarTodas();

    assertThat(resultado).extracting(Marca::nombre).contains("Andes Wear", "Zeta Sport");
    assertThat(resultado).extracting(Marca::nombre).isSorted();
  }

  /**
   * La migración de las marcas reales, comprobada de verdad y no dada por hecha.
   *
   * <p>Sin estas filas el catálogo real no se puede cargar: {@code POST /api/v1/admin/productos}
   * exige un {@code marcaId} que ya exista y no hay endpoint que cree marcas.
   */
  @Test
  void laMigracionDejaLasMarcasRealesDelNegocio() {
    assertThat(repositorio.listarTodas())
        .extracting(Marca::nombre)
        .contains(
            "Apple",
            "Bose",
            "Honor",
            "JBL",
            "Lenovo",
            "Motorola",
            "Nintendo",
            "Realme",
            "Samsung",
            "Sony",
            "TCL",
            "Xiaomi");
  }

  /**
   * Y ninguna de ellas aparece en la vitrina mientras no tenga un producto publicado.
   *
   * <p>Es la condición que hizo seguro dar de alta las doce de una vez en vez de solo las del
   * primer lote: antes del 19 de septiembre de 2026 esta migración habría metido doce filtros
   * vacíos en la tienda.
   */
  @Test
  void lasMarcasRecienMigradasNoLleganALaVitrina() {
    assertThat(repositorio.listarConProductosPublicados())
        .extracting(Marca::nombre)
        .doesNotContain("Xiaomi", "Samsung", "JBL");
  }

  /**
   * El índice único de {@code V54}, comprobado disparándolo.
   *
   * <p>La migración nació con un {@code on conflict do nothing} sin columna, que sin restricción no
   * protege de nada y solo aparenta hacerlo. Dos marcas con el mismo nombre repartirían los
   * productos entre las dos y la vitrina ofrecería "Xiaomi" dos veces, cada una con media marca.
   */
  @Test
  void dosMarcasNoPuedenLlamarseIgual() {
    assertThatThrownBy(
            () -> marcas.saveAndFlush(new MarcaJpaEntity(UUID.randomUUID(), "Xiaomi", AHORA)))
        .isInstanceOf(DataIntegrityViolationException.class);
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
