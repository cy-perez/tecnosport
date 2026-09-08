package co.tecnosport.api.infrastructure.catalogo;

import static org.assertj.core.api.Assertions.assertThat;

import co.tecnosport.api.application.catalogo.EntradaMapaDelSitio;
import co.tecnosport.api.domain.compartido.Slug;
import co.tecnosport.api.infrastructure.catalogo.entidad.CategoriaJpaEntity;
import co.tecnosport.api.infrastructure.catalogo.entidad.MarcaJpaEntity;
import co.tecnosport.api.infrastructure.catalogo.entidad.ProductoJpaEntity;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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

/**
 * Contra Postgres real y no con un doble: lo único que hay que probar aquí es el SQL —el nombre de
 * las columnas, el literal del estado y la conversión del {@code timestamptz}—, y eso un doble no
 * lo ejerce. Ver `docs/06-testing.md`.
 */
@SpringBootTest
@Testcontainers
@Transactional
class RepositorioMapaDelSitioJpaTest {

  @Container @ServiceConnection
  static PostgreSQLContainer postgres =
      new PostgreSQLContainer(DockerImageName.parse("postgres:16"));

  @Autowired private RepositorioMapaDelSitioJpa repositorio;
  @Autowired private jakarta.persistence.EntityManager entityManager;
  @Autowired private MarcaJpaRepository marcas;
  @Autowired private CategoriaJpaRepository categorias;
  @Autowired private ProductoJpaRepository productos;

  /**
   * El filtro es la razón de ser de esta consulta: un borrador en el sitemap invita a Google a
   * indexar una ficha que responde 404 al público.
   */
  @Test
  void soloDevuelveLosProductosPublicados() {
    MarcaJpaEntity marca = marca("TecnoSport");
    CategoriaJpaEntity categoria = categoria("Ropa", "ropa-mapa", "ROPA_Y_CALZADO");
    producto("Publicado", "publicado-mapa", "PUBLICADO", marca, categoria, Instant.now());
    producto("Borrador", "borrador-mapa", "BORRADOR", marca, categoria, Instant.now());

    List<EntradaMapaDelSitio> entradas = consultar(50);

    assertThat(entradas).extracting(e -> e.slug().valor()).containsExactly("publicado-mapa");
  }

  /** Sin fecha no hay {@code <lastmod>}, y con una fecha equivocada el `<lastmod>` miente. */
  @Test
  void devuelveLaFechaDeUltimaActualizacionDelProducto() {
    MarcaJpaEntity marca = marca("TecnoSport");
    CategoriaJpaEntity categoria = categoria("Ropa", "ropa-fecha", "ROPA_Y_CALZADO");
    Instant actualizado = Instant.parse("2026-03-04T15:30:00Z");
    producto("Con fecha", "con-fecha-mapa", "PUBLICADO", marca, categoria, actualizado);

    List<EntradaMapaDelSitio> entradas = consultar(50);

    assertThat(entradas)
        .singleElement()
        .isEqualTo(new EntradaMapaDelSitio(new Slug("con-fecha-mapa"), actualizado));
  }

  /**
   * El orden importa el día que el catálogo pase del tope del formato: lo que se queda fuera tiene
   * que ser lo más viejo, no una franja arbitraria.
   */
  @Test
  void ordenaPorMasRecientementeActualizadoPrimero() {
    MarcaJpaEntity marca = marca("TecnoSport");
    CategoriaJpaEntity categoria = categoria("Ropa", "ropa-orden", "ROPA_Y_CALZADO");
    Instant ahora = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    producto("Viejo", "viejo-mapa", "PUBLICADO", marca, categoria, ahora.minusSeconds(3600));
    producto("Nuevo", "nuevo-mapa", "PUBLICADO", marca, categoria, ahora);

    List<EntradaMapaDelSitio> entradas = consultar(50);

    assertThat(entradas)
        .extracting(e -> e.slug().valor())
        .containsExactly("nuevo-mapa", "viejo-mapa");
  }

  @Test
  void respetaElLimiteQueLePasaElCasoDeUso() {
    MarcaJpaEntity marca = marca("TecnoSport");
    CategoriaJpaEntity categoria = categoria("Ropa", "ropa-limite", "ROPA_Y_CALZADO");
    Instant ahora = Instant.now();
    producto("Uno", "uno-mapa", "PUBLICADO", marca, categoria, ahora);
    producto("Dos", "dos-mapa", "PUBLICADO", marca, categoria, ahora.minusSeconds(60));

    assertThat(consultar(1)).hasSize(1);
  }

  @Test
  void unCatalogoSinPublicadosDevuelveListaVacia() {
    assertThat(consultar(50)).isEmpty();
  }

  /**
   * La consulta es SQL nativo por {@code JdbcTemplate}, y eso **no** dispara el auto-flush de
   * Hibernate: sin este {@code flush()} los productos recién guardados siguen solo en el contexto
   * de persistencia y la consulta ve una tabla vacía. Misma trampa documentada en {@link
   * RepositorioProductosJpaTest}.
   */
  private List<EntradaMapaDelSitio> consultar(int limite) {
    entityManager.flush();
    return repositorio.listarProductosPublicados(limite);
  }

  private MarcaJpaEntity marca(String nombre) {
    return marcas.save(new MarcaJpaEntity(UUID.randomUUID(), nombre, Instant.now()));
  }

  private CategoriaJpaEntity categoria(String nombre, String slug, String linea) {
    return categorias.save(
        new CategoriaJpaEntity(UUID.randomUUID(), nombre, slug, linea, Instant.now()));
  }

  private ProductoJpaEntity producto(
      String nombre,
      String slug,
      String estado,
      MarcaJpaEntity marca,
      CategoriaJpaEntity categoria,
      Instant actualizadoEn) {
    return productos.save(
        new ProductoJpaEntity(
            UUID.randomUUID(),
            nombre,
            slug,
            "",
            marca.getId(),
            categoria.getId(),
            estado,
            actualizadoEn,
            actualizadoEn));
  }
}
