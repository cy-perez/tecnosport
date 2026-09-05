package co.tecnosport.api.infrastructure.catalogo;

import static org.assertj.core.api.Assertions.assertThat;

import co.tecnosport.api.application.catalogo.FiltroProductos;
import co.tecnosport.api.application.catalogo.OrdenProductos;
import co.tecnosport.api.application.catalogo.ProductosPaginados;
import co.tecnosport.api.application.compartido.ResultadoPaginado;
import co.tecnosport.api.domain.catalogo.Categoria;
import co.tecnosport.api.domain.catalogo.EstadoProducto;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.catalogo.Marca;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.compartido.Slug;
import co.tecnosport.api.infrastructure.catalogo.entidad.AtributoJpaEntity;
import co.tecnosport.api.infrastructure.catalogo.entidad.CategoriaJpaEntity;
import co.tecnosport.api.infrastructure.catalogo.entidad.ImagenProductoJpaEntity;
import co.tecnosport.api.infrastructure.catalogo.entidad.MarcaJpaEntity;
import co.tecnosport.api.infrastructure.catalogo.entidad.ProductoJpaEntity;
import co.tecnosport.api.infrastructure.catalogo.entidad.SetRotacionJpaEntity;
import co.tecnosport.api.infrastructure.catalogo.entidad.VarianteAtributoValorJpaEntity;
import co.tecnosport.api.infrastructure.catalogo.entidad.VarianteJpaEntity;
import java.math.BigDecimal;
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

// @Transactional: cada prueba corre en su propia transacción, revertida al
// terminar — los datos de una prueba no contaminan a las demás en el mismo
// contenedor de Postgres. JdbcTemplate participa en la misma transacción que
// JPA porque comparten el DataSource administrado por Spring.
@SpringBootTest
@Testcontainers
@Transactional
class RepositorioProductosJpaTest {

  @Container @ServiceConnection
  static PostgreSQLContainer postgres =
      new PostgreSQLContainer(DockerImageName.parse("postgres:16"));

  @Autowired private RepositorioProductosJpa repositorio;
  @Autowired private MarcaJpaRepository marcas;
  @Autowired private CategoriaJpaRepository categorias;
  @Autowired private AtributoJpaRepository atributos;
  @Autowired private ProductoJpaRepository productos;
  @Autowired private VarianteJpaRepository variantes;
  @Autowired private VarianteAtributoValorJpaRepository valoresAtributo;
  @Autowired private ImagenProductoJpaRepository imagenes;
  @Autowired private SetRotacionJpaRepository setsRotacion;
  @Autowired private jakarta.persistence.EntityManager entityManager;

  @Test
  void buscarPorSlugHidrataUnProductoCompletoConAtributosEImagenesYSetDeRotacion() {
    MarcaJpaEntity marca = marca("TecnoSport");
    CategoriaJpaEntity categoria =
        categoria("Ropa deportiva", "ropa-deportiva-t1", "ROPA_Y_CALZADO");
    AtributoJpaEntity color = atributo("Color", "COLOR");
    ProductoJpaEntity producto =
        producto("Camiseta test", "camiseta-test-1", "PUBLICADO", marca, categoria);
    VarianteJpaEntity variante = variante(producto, "SKU-TEST-1", "89900");
    valoresAtributo.save(
        new VarianteAtributoValorJpaEntity(
            UUID.randomUUID(), variante.getId(), color.getId(), "Azul marino", "#1E3A8A"));
    imagenPrincipal(producto);
    SetRotacionJpaEntity set =
        setsRotacion.save(
            new SetRotacionJpaEntity(
                UUID.randomUUID(),
                producto.getId(),
                null,
                "PUBLICADO",
                "admin",
                Instant.now(),
                "iPhone 14",
                "v1"));
    for (int orden = 0; orden < 4; orden++) {
      fotogramaRotacion(producto, set, orden);
    }

    Optional<Producto> encontrado = repositorio.buscarPorSlug(new Slug("camiseta-test-1"));

    assertThat(encontrado).isPresent();
    Producto p = encontrado.orElseThrow();
    assertThat(p.nombre()).isEqualTo("Camiseta test");
    assertThat(p.marca().nombre()).isEqualTo("TecnoSport");
    assertThat(p.categoria().linea()).isEqualTo(LineaCatalogo.ROPA_Y_CALZADO);
    assertThat(p.variantes()).hasSize(1);
    assertThat(p.variantes().get(0).sku().valor()).isEqualTo("SKU-TEST-1");
    assertThat(p.variantes().get(0).atributos().get(0).colorHex()).isEqualTo("#1E3A8A");
    assertThat(p.imagenPrincipal()).isPresent();
    assertThat(p.setRotacion()).isPresent();
    assertThat(p.setRotacion().orElseThrow().fotogramas()).hasSize(4);
  }

  @Test
  void buscarPorSlugExcluyeVariantesInactivas() {
    MarcaJpaEntity marca = marca("TecnoSport");
    CategoriaJpaEntity categoria = categoria("Celulares", "celulares-t7", "CELULARES");
    ProductoJpaEntity producto =
        producto("Celular con variante de baja", "celular-t7", "PUBLICADO", marca, categoria);
    variante(producto, "SKU-T7-ACTIVA", "1000000");
    variantes.save(
        new VarianteJpaEntity(
            UUID.randomUUID(),
            producto.getId(),
            "SKU-T7-INACTIVA",
            new BigDecimal("1000000"),
            new BigDecimal("0.19"),
            0,
            null,
            "INACTIVA",
            Instant.now()));
    imagenPrincipal(producto);

    Optional<Producto> encontrado = repositorio.buscarPorSlug(new Slug("celular-t7"));

    assertThat(encontrado).isPresent();
    assertThat(encontrado.orElseThrow().variantes())
        .extracting(v -> v.sku().valor())
        .containsExactly("SKU-T7-ACTIVA");
  }

  @Test
  void buscarPorSlugDevuelveUnProductoEnBorrador() {
    MarcaJpaEntity marca = marca("TecnoSport");
    CategoriaJpaEntity categoria = categoria("Bolsos", "bolsos-t2", "BOLSOS");
    producto("Morral en borrador", "morral-borrador-t2", "BORRADOR", marca, categoria);

    Optional<Producto> encontrado = repositorio.buscarPorSlug(new Slug("morral-borrador-t2"));

    assertThat(encontrado).isPresent();
    assertThat(encontrado.orElseThrow().estado()).isEqualTo(EstadoProducto.BORRADOR);
  }

  @Test
  void buscarSoloDevuelveProductosPublicados() {
    MarcaJpaEntity marca = marca("TecnoSport");
    CategoriaJpaEntity categoria = categoria("Celulares", "celulares-t3", "CELULARES");
    ProductoJpaEntity publicado =
        producto("Celular publicado", "celular-publicado-t3", "PUBLICADO", marca, categoria);
    variante(publicado, "SKU-T3-PUB", "1000000");
    imagenPrincipal(publicado);
    ProductoJpaEntity borrador =
        producto("Celular borrador", "celular-borrador-t3", "BORRADOR", marca, categoria);
    variante(borrador, "SKU-T3-BOR", "1000000");

    // buscar() usa SQL nativo por JdbcTemplate: no dispara el auto-flush de
    // Hibernate que sí ocurre al consultar por JPA (buscarPorSlug), así que
    // hace falta forzarlo para que la fila recién guardada sea visible aquí.
    entityManager.flush();
    ResultadoPaginado<Producto> resultado =
        repositorio.buscar(FiltroProductos.vacio(), OrdenProductos.MAS_RECIENTES, null, 10);

    assertThat(resultado.items())
        .extracting(Producto::slug)
        .extracting(Slug::valor)
        .containsExactly("celular-publicado-t3");
  }

  @Test
  void buscarFiltraPorCategoriaYPorLinea() {
    MarcaJpaEntity marca = marca("TecnoSport");
    CategoriaJpaEntity ropa = categoria("Ropa deportiva", "ropa-deportiva-t4", "ROPA_Y_CALZADO");
    CategoriaJpaEntity bolsos = categoria("Bolsos", "bolsos-t4", "BOLSOS");
    ProductoJpaEntity productoRopa =
        producto("Camiseta t4", "camiseta-t4", "PUBLICADO", marca, ropa);
    variante(productoRopa, "SKU-T4-ROPA", "90000");
    imagenPrincipal(productoRopa);
    ProductoJpaEntity productoBolso =
        producto("Morral t4", "morral-t4", "PUBLICADO", marca, bolsos);
    variante(productoBolso, "SKU-T4-BOLSO", "150000");
    imagenPrincipal(productoBolso);

    entityManager.flush();
    ResultadoPaginado<Producto> porCategoria =
        repositorio.buscar(
            new FiltroProductos(new Slug("ropa-deportiva-t4"), null, null, null, null, null),
            OrdenProductos.MAS_RECIENTES,
            null,
            10);
    ResultadoPaginado<Producto> porLinea =
        repositorio.buscar(
            new FiltroProductos(null, null, LineaCatalogo.BOLSOS, null, null, null),
            OrdenProductos.MAS_RECIENTES,
            null,
            10);

    assertThat(porCategoria.items())
        .extracting(Producto::slug)
        .extracting(Slug::valor)
        .containsExactly("camiseta-t4");
    assertThat(porLinea.items())
        .extracting(Producto::slug)
        .extracting(Slug::valor)
        .containsExactly("morral-t4");
  }

  @Test
  void buscarPaginaConCursorSinRepetirNiSaltarProductos() {
    MarcaJpaEntity marca = marca("TecnoSport");
    CategoriaJpaEntity categoria = categoria("Bolsos", "bolsos-t5", "BOLSOS");
    List<String> slugs = List.of("morral-t5-barato", "morral-t5-medio", "morral-t5-caro");
    List<String> precios = List.of("100000", "200000", "300000");
    for (int i = 0; i < slugs.size(); i++) {
      ProductoJpaEntity p =
          producto("Morral " + slugs.get(i), slugs.get(i), "PUBLICADO", marca, categoria);
      variante(p, "SKU-T5-" + i, precios.get(i));
      imagenPrincipal(p);
    }

    entityManager.flush();
    ResultadoPaginado<Producto> primeraPagina =
        repositorio.buscar(FiltroProductos.vacio(), OrdenProductos.PRECIO_ASC, null, 2);
    assertThat(primeraPagina.items()).hasSize(2);
    assertThat(primeraPagina.cursorSiguiente()).isNotNull();

    ResultadoPaginado<Producto> segundaPagina =
        repositorio.buscar(
            FiltroProductos.vacio(), OrdenProductos.PRECIO_ASC, primeraPagina.cursorSiguiente(), 2);
    assertThat(segundaPagina.items()).hasSize(1);
    assertThat(segundaPagina.cursorSiguiente()).isNull();

    List<String> slugsVistos =
        java.util.stream.Stream.concat(
                primeraPagina.items().stream(), segundaPagina.items().stream())
            .map(Producto::slug)
            .map(Slug::valor)
            .toList();
    assertThat(slugsVistos)
        .containsExactly("morral-t5-barato", "morral-t5-medio", "morral-t5-caro");
  }

  @Test
  void buscarConTextoEncuentraPorSimilitudAunqueNoSeaSubstringExacto() {
    MarcaJpaEntity marca = marca("TecnoSport");
    CategoriaJpaEntity categoria =
        categoria("Ropa deportiva", "ropa-deportiva-t6", "ROPA_Y_CALZADO");
    ProductoJpaEntity camiseta =
        producto("Camiseta running Dry-Fit", "camiseta-t6", "PUBLICADO", marca, categoria);
    variante(camiseta, "SKU-T6-CAM", "89900");
    imagenPrincipal(camiseta);
    ProductoJpaEntity morral =
        producto("Morral urbano", "morral-t6", "PUBLICADO", marca, categoria);
    variante(morral, "SKU-T6-MOR", "150000");
    imagenPrincipal(morral);

    entityManager.flush();
    ResultadoPaginado<Producto> resultado =
        repositorio.buscar(
            new FiltroProductos(null, null, null, null, null, "camiseta"),
            OrdenProductos.RELEVANCIA,
            null,
            10);

    assertThat(resultado.items())
        .extracting(Producto::slug)
        .extracting(Slug::valor)
        .containsExactly("camiseta-t6");
  }

  @Test
  void buscarParaAdminIncluyeBorradorYPublicadoPaginadoPorPagina() {
    MarcaJpaEntity marca = marca("TecnoSport");
    CategoriaJpaEntity categoria = categoria("Celulares", "celulares-t8", "CELULARES");
    producto("Celular publicado t8", "celular-publicado-t8", "PUBLICADO", marca, categoria);
    producto("Celular borrador t8", "celular-borrador-t8", "BORRADOR", marca, categoria);

    ProductosPaginados resultado = repositorio.buscarParaAdmin(0, 1);

    assertThat(resultado.items()).hasSize(1);
    assertThat(resultado.pagina()).isEqualTo(0);
    assertThat(resultado.totalPaginas()).isEqualTo(2);
    assertThat(resultado.totalProductos()).isEqualTo(2);

    ProductosPaginados segundaPagina = repositorio.buscarParaAdmin(1, 1);
    assertThat(segundaPagina.items()).hasSize(1);

    List<String> slugsVistos =
        java.util.stream.Stream.concat(resultado.items().stream(), segundaPagina.items().stream())
            .map(Producto::slug)
            .map(Slug::valor)
            .toList();
    assertThat(slugsVistos)
        .containsExactlyInAnyOrder("celular-publicado-t8", "celular-borrador-t8");
  }

  @Test
  void guardarInsertaUnProductoNuevoYQuedaLegibleParaBuscarPorSlug() {
    MarcaJpaEntity marca = marca("TecnoSport");
    CategoriaJpaEntity categoria = categoria("Bolsos", "bolsos-t9", "BOLSOS");

    Producto producto =
        Producto.crear(
            "Morral urbano t9",
            new Slug("morral-urbano-t9"),
            "Descripción",
            new Marca(marca.getId(), "TecnoSport"),
            new Categoria(
                categoria.getId(), "Bolsos", new Slug("bolsos-t9"), LineaCatalogo.BOLSOS));

    repositorio.guardar(producto);

    Optional<Producto> encontrado = repositorio.buscarPorSlug(new Slug("morral-urbano-t9"));
    assertThat(encontrado).isPresent();
    Producto p = encontrado.orElseThrow();
    assertThat(p.id()).isEqualTo(producto.id());
    assertThat(p.nombre()).isEqualTo("Morral urbano t9");
    assertThat(p.estado()).isEqualTo(EstadoProducto.BORRADOR);
    assertThat(p.marca().nombre()).isEqualTo("TecnoSport");
  }

  private MarcaJpaEntity marca(String nombre) {
    return marcas.save(new MarcaJpaEntity(UUID.randomUUID(), nombre, Instant.now()));
  }

  private CategoriaJpaEntity categoria(String nombre, String slug, String linea) {
    return categorias.save(
        new CategoriaJpaEntity(UUID.randomUUID(), nombre, slug, linea, Instant.now()));
  }

  private AtributoJpaEntity atributo(String nombre, String tipo) {
    return atributos.save(
        new AtributoJpaEntity(UUID.randomUUID(), nombre, tipo, List.of(), Instant.now()));
  }

  private ProductoJpaEntity producto(
      String nombre,
      String slug,
      String estado,
      MarcaJpaEntity marca,
      CategoriaJpaEntity categoria) {
    Instant ahora = Instant.now();
    return productos.save(
        new ProductoJpaEntity(
            UUID.randomUUID(),
            nombre,
            slug,
            "",
            marca.getId(),
            categoria.getId(),
            estado,
            ahora,
            ahora));
  }

  private VarianteJpaEntity variante(ProductoJpaEntity producto, String sku, String precio) {
    return variantes.save(
        new VarianteJpaEntity(
            UUID.randomUUID(),
            producto.getId(),
            sku,
            new BigDecimal(precio),
            new BigDecimal("0.19"),
            5,
            null,
            "ACTIVA",
            Instant.now()));
  }

  private void imagenPrincipal(ProductoJpaEntity producto) {
    String url = "https://picsum.photos/seed/" + producto.getSlug() + "/800/600";
    imagenes.save(
        new ImagenProductoJpaEntity(
            UUID.randomUUID(),
            producto.getId(),
            null,
            null,
            "PRINCIPAL",
            0,
            url,
            url,
            800,
            600,
            1000,
            "hash-" + producto.getSlug(),
            "alt es",
            "alt en",
            Instant.now()));
  }

  private void fotogramaRotacion(ProductoJpaEntity producto, SetRotacionJpaEntity set, int orden) {
    String url = "https://picsum.photos/seed/" + producto.getSlug() + "-" + orden + "/800/600";
    imagenes.save(
        new ImagenProductoJpaEntity(
            UUID.randomUUID(),
            producto.getId(),
            null,
            set.getId(),
            "ROTACION",
            orden,
            url,
            url,
            800,
            600,
            1000,
            "hash-" + producto.getSlug() + "-" + orden,
            "",
            "",
            Instant.now()));
  }
}
