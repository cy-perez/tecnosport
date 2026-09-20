package co.tecnosport.api.infrastructure.catalogo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.tecnosport.api.application.catalogo.MarcaYaExisteException;
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
   * <p>Sin estas filas el catálogo real no se podía cargar: {@code POST /api/v1/admin/productos}
   * exige un {@code marcaId} que ya exista, y cuando se escribió {@code V54} no había endpoint que
   * creara marcas. Desde {@code ADR-0047} sí lo hay, y estas doce siguen entrando por migración:
   * son el arranque que toda instalación necesita, no el crecimiento del catálogo.
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
   * El índice único de {@code marca.nombre}, comprobado disparándolo. Nació en {@code V54} sobre la
   * columna tal cual y {@code V56} lo pasó a {@code lower(nombre)}; el duplicado exacto que esta
   * prueba dispara lo rechazan los dos, así que sigue valiendo igual.
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

  /**
   * El camino que nadie había recorrido: hasta hoy ninguna prueba escribía una marca <b>por el
   * adaptador</b>. Es la lección del 19 de septiembre con las variantes sin medir — el {@code 500}
   * que ninguna capa de arriba puede ver solo lo atrapa una prueba que guarde de verdad.
   */
  @Test
  void guardarEscribeLaMarcaYListarTodasLaDevuelve() {
    Marca marca = Marca.crear("Andes Wear");

    repositorio.guardar(marca);

    assertThat(repositorio.buscarPorId(marca.id()).orElseThrow().nombre()).isEqualTo("Andes Wear");
    assertThat(repositorio.listarTodas()).extracting(Marca::nombre).contains("Andes Wear");
  }

  @Test
  void existeConNombreNoDistingueMayusculas() {
    repositorio.guardar(Marca.crear("Andes Wear"));

    assertThat(repositorio.existeConNombre("andes wear")).isTrue();
    assertThat(repositorio.existeConNombre("ANDES WEAR")).isTrue();
    assertThat(repositorio.existeConNombre("Andes Wearable")).isFalse();
  }

  /**
   * {@code V56} disparado. "Xiaomi" la sembró {@code V54}, así que esta fila entra en cualquier
   * base del proyecto.
   *
   * <p>La comprobación del caso de uso no sustituye a esta: entre su {@code existeConNombre} y su
   * {@code guardar} hay una ventana por la que se puede colar otra petición, y lo único que cierra
   * esa ventana es la base.
   */
  @Test
  void laBaseRechazaDosNombresQueSoloSeDiferencianEnLasMayusculas() {
    assertThatThrownBy(
            () -> marcas.saveAndFlush(new MarcaJpaEntity(UUID.randomUUID(), "xiaomi", AHORA)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  /**
   * La otra mitad de lo anterior: por el adaptador, la violación sale traducida y no como excepción
   * de JPA — {@code apps/api/CLAUDE.md}, "ninguna excepción de JPA sale de infrastructure".
   *
   * <p>Es el camino de la carrera: {@code CrearMarca} ya preguntó y le dijeron que no existía. Sin
   * el {@code saveAndFlush} del adaptador esta prueba pasaría en verde y el fallo aparecería al
   * confirmar la transacción, fuera de cualquier {@code catch}.
   */
  @Test
  void guardarTraduceLaViolacionDelUnicoEnUnaExcepcionDeAplicacion() {
    assertThatThrownBy(() -> repositorio.guardar(Marca.crear("xiaomi")))
        .isInstanceOf(MarcaYaExisteException.class);
  }
}
