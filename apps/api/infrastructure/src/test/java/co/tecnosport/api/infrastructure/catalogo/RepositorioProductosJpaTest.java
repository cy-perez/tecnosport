package co.tecnosport.api.infrastructure.catalogo;

import static org.assertj.core.api.Assertions.assertThat;

import co.tecnosport.api.application.catalogo.FiltroProductos;
import co.tecnosport.api.application.catalogo.MedidaDeVariante;
import co.tecnosport.api.application.catalogo.OrdenProductos;
import co.tecnosport.api.application.catalogo.ProductosPaginados;
import co.tecnosport.api.application.catalogo.VarianteActiva;
import co.tecnosport.api.application.compartido.ResultadoPaginado;
import co.tecnosport.api.domain.catalogo.Atributo;
import co.tecnosport.api.domain.catalogo.Categoria;
import co.tecnosport.api.domain.catalogo.EstadoProducto;
import co.tecnosport.api.domain.catalogo.ImagenProducto;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.catalogo.Marca;
import co.tecnosport.api.domain.catalogo.Paquete;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.catalogo.TipoAtributo;
import co.tecnosport.api.domain.catalogo.TipoImagen;
import co.tecnosport.api.domain.catalogo.ValorAtributo;
import co.tecnosport.api.domain.catalogo.Variante;
import co.tecnosport.api.domain.catalogo.VarianteDeImagen;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.HashContenido;
import co.tecnosport.api.domain.compartido.Sku;
import co.tecnosport.api.domain.compartido.Slug;
import co.tecnosport.api.infrastructure.catalogo.entidad.AtributoJpaEntity;
import co.tecnosport.api.infrastructure.catalogo.entidad.CategoriaJpaEntity;
import co.tecnosport.api.infrastructure.catalogo.entidad.ImagenProductoJpaEntity;
import co.tecnosport.api.infrastructure.catalogo.entidad.MarcaJpaEntity;
import co.tecnosport.api.infrastructure.catalogo.entidad.ProductoJpaEntity;
import co.tecnosport.api.infrastructure.catalogo.entidad.SetRotacionJpaEntity;
import co.tecnosport.api.infrastructure.catalogo.entidad.VarianteAtributoValorJpaEntity;
import co.tecnosport.api.infrastructure.catalogo.entidad.VarianteImagenJpaEntity;
import co.tecnosport.api.infrastructure.catalogo.entidad.VarianteJpaEntity;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
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
  @Autowired private VarianteImagenJpaRepository variantesDeImagen;
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
                4,
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
  void buscarPorSlugIgnoraUnSetEnBorradorYDevuelveElPublicado() {
    // Recapturar un producto deja dos sets a la vez: el que ya se ve y el que se está armando.
    // Antes de este filtro, el borrador podía ganar y la ficha se quedaba sin visor.
    MarcaJpaEntity marca = marca("TecnoSport");
    CategoriaJpaEntity categoria = categoria("Bolsos", "bolsos-t9", "BOLSOS");
    ProductoJpaEntity producto =
        producto("Morral recapturado", "morral-t9", "PUBLICADO", marca, categoria);
    variante(producto, "SKU-T9", "150000");
    imagenPrincipal(producto);

    SetRotacionJpaEntity borrador =
        setsRotacion.save(
            new SetRotacionJpaEntity(
                UUID.randomUUID(),
                producto.getId(),
                null,
                8,
                "BORRADOR",
                "admin",
                Instant.now(),
                "iPhone 14",
                "v1"));
    SetRotacionJpaEntity publicado =
        setsRotacion.save(
            new SetRotacionJpaEntity(
                UUID.randomUUID(),
                producto.getId(),
                null,
                4,
                "PUBLICADO",
                "admin",
                Instant.now(),
                "iPhone 14",
                "v1"));
    for (int orden = 0; orden < 4; orden++) {
      fotogramaRotacion(producto, publicado, orden);
    }
    assertThat(borrador.getId()).isNotEqualTo(publicado.getId());

    Optional<Producto> encontrado = repositorio.buscarPorSlug(new Slug("morral-t9"));

    assertThat(encontrado).isPresent();
    assertThat(encontrado.orElseThrow().setRotacion()).isPresent();
    assertThat(encontrado.orElseThrow().setRotacion().orElseThrow().id())
        .isEqualTo(publicado.getId());
  }

  @Test
  void buscarPorSlugExcluyeVariantesInactivas() {
    MarcaJpaEntity marca = marca("TecnoSport");
    CategoriaJpaEntity categoria = categoria("Celulares", "celulares-t7", "TECNOLOGIA");
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
            null,
            400,
            18,
            10,
            6,
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
    CategoriaJpaEntity categoria = categoria("Celulares", "celulares-t3", "TECNOLOGIA");
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
    CategoriaJpaEntity categoria = categoria("Celulares", "celulares-t8", "TECNOLOGIA");
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

  @Test
  void guardarLaImagenPrincipalGuardaSusTresVariantesYVuelvenOrdenadas() {
    MarcaJpaEntity marca = marca("TecnoSport");
    CategoriaJpaEntity categoria = categoria("Bolsos", "bolsos-v1", "BOLSOS");
    ProductoJpaEntity producto = producto("Morral v1", "morral-v1", "PUBLICADO", marca, categoria);

    repositorio.guardarImagenPrincipal(
        producto.getId(),
        ImagenProducto.crear(
            TipoImagen.PRINCIPAL,
            0,
            List.of(
                new VarianteDeImagen(1200, "https://x/morral-1200.avif", 58_000),
                new VarianteDeImagen(480, "https://x/morral-480.avif", 12_000),
                new VarianteDeImagen(800, "https://x/morral-800.avif", 30_000)),
            "https://x/morral-1200.jpg",
            900,
            new HashContenido(hashDePrueba("morral-v1")),
            "alt es",
            "alt en"));
    entityManager.flush();
    entityManager.clear();

    ImagenProducto imagen =
        repositorio.buscarPorId(producto.getId()).orElseThrow().imagenPrincipal().orElseThrow();

    assertThat(imagen.variantes().stream().map(VarianteDeImagen::ancho))
        .containsExactly(480, 800, 1200);
    // La URL, el ancho y los bytes son los de la mayor, no los de la primera que llegó.
    assertThat(imagen.url()).isEqualTo("https://x/morral-1200.avif");
    assertThat(imagen.ancho()).isEqualTo(1200);
    assertThat(imagen.bytes()).isEqualTo(58_000);
    assertThat(imagen.urlVistaPrevia()).contains("https://x/morral-1200.jpg");
  }

  /**
   * El reemplazo de la principal borra la fila vieja, y la base se lleva sus variantes con el
   * {@code on delete cascade} de la V60. Sin eso quedarían filas apuntando a objetos que ya nadie
   * sirve, y nadie volvería a mirarlas.
   */
  @Test
  void reemplazarLaImagenPrincipalSeLlevaLasVariantesDeLaAnterior() {
    MarcaJpaEntity marca = marca("TecnoSport");
    CategoriaJpaEntity categoria = categoria("Bolsos", "bolsos-v2", "BOLSOS");
    ProductoJpaEntity producto = producto("Morral v2", "morral-v2", "PUBLICADO", marca, categoria);

    repositorio.guardarImagenPrincipal(
        producto.getId(), imagenCon(List.of(480, 800, 1200), "vieja"));
    entityManager.flush();
    repositorio.guardarImagenPrincipal(producto.getId(), imagenCon(List.of(800), "nueva"));
    entityManager.flush();
    entityManager.clear();

    ImagenProducto imagen =
        repositorio.buscarPorId(producto.getId()).orElseThrow().imagenPrincipal().orElseThrow();
    assertThat(imagen.variantes()).hasSize(1);
    assertThat(variantesDeImagen.count()).isEqualTo(1);
  }

  private ImagenProducto imagenCon(List<Integer> anchos, String semilla) {
    return ImagenProducto.crear(
        TipoImagen.PRINCIPAL,
        0,
        anchos.stream()
            .map(a -> new VarianteDeImagen(a, "https://x/" + semilla + "-" + a + ".avif", a * 50L))
            .toList(),
        null,
        900,
        new HashContenido(hashDePrueba(semilla)),
        "alt es",
        "alt en");
  }

  @Test
  void buscarPorIdDevuelveUnProductoEnCualquierEstado() {
    MarcaJpaEntity marca = marca("TecnoSport");
    CategoriaJpaEntity categoria = categoria("Bolsos", "bolsos-t10", "BOLSOS");
    ProductoJpaEntity borrador = producto("Morral t10", "morral-t10", "BORRADOR", marca, categoria);

    Optional<Producto> encontrado = repositorio.buscarPorId(borrador.getId());

    assertThat(encontrado).isPresent();
    assertThat(encontrado.orElseThrow().estado()).isEqualTo(EstadoProducto.BORRADOR);
  }

  @Test
  void buscarPorIdDevuelveVacioSiNoExiste() {
    assertThat(repositorio.buscarPorId(UUID.randomUUID())).isEmpty();
  }

  @Test
  void actualizarCambiaLosDatosBasicosYConservaCreadoEnPeroActualizaActualizadoEn() {
    MarcaJpaEntity marcaOriginal = marca("TecnoSport");
    CategoriaJpaEntity categoriaOriginal = categoria("Bolsos", "bolsos-t11", "BOLSOS");
    ProductoJpaEntity entidadOriginal =
        producto("Morral t11", "morral-t11", "BORRADOR", marcaOriginal, categoriaOriginal);
    MarcaJpaEntity nuevaMarca = marca("Under Trail");
    CategoriaJpaEntity nuevaCategoria = categoria("Celulares", "celulares-t11", "TECNOLOGIA");

    Producto producto =
        Producto.crear(
            "Morral t11 renovado",
            new Slug("morral-t11"),
            "Nueva descripción",
            new Marca(nuevaMarca.getId(), "Under Trail"),
            new Categoria(
                nuevaCategoria.getId(),
                "Celulares",
                new Slug("celulares-t11"),
                LineaCatalogo.TECNOLOGIA));
    // Producto.crear() genera un id nuevo, el update tiene que ir contra el id ya existente.
    Producto productoConIdExistente =
        new Producto(
            entidadOriginal.getId(),
            producto.nombre(),
            producto.slug(),
            producto.descripcion(),
            producto.marca(),
            producto.categoria(),
            EstadoProducto.BORRADOR,
            null,
            List.of(),
            null,
            List.of());

    repositorio.actualizar(productoConIdExistente);

    Optional<Producto> encontrado = repositorio.buscarPorId(entidadOriginal.getId());
    assertThat(encontrado).isPresent();
    Producto p = encontrado.orElseThrow();
    assertThat(p.nombre()).isEqualTo("Morral t11 renovado");
    assertThat(p.descripcion()).isEqualTo("Nueva descripción");
    assertThat(p.marca().nombre()).isEqualTo("Under Trail");
    assertThat(p.categoria().nombre()).isEqualTo("Celulares");
    assertThat(p.slug().valor()).isEqualTo("morral-t11");

    ProductoJpaEntity entidadActualizada =
        productos.findById(entidadOriginal.getId()).orElseThrow();
    assertThat(entidadActualizada.getCreadoEn()).isEqualTo(entidadOriginal.getCreadoEn());
    assertThat(entidadActualizada.getActualizadoEn())
        .isAfterOrEqualTo(entidadOriginal.getActualizadoEn());
    assertThat(entidadActualizada.getActualizadoEn())
        .isNotEqualTo(entidadActualizada.getCreadoEn());
  }

  @Test
  void agregarVarianteLaPersisteConSusAtributosYQuedaLegibleAlHidratarElProducto() {
    MarcaJpaEntity marca = marca("TecnoSport");
    CategoriaJpaEntity categoria =
        categoria("Ropa deportiva", "ropa-deportiva-t12", "ROPA_Y_CALZADO");
    ProductoJpaEntity productoJpa =
        producto("Camiseta t12", "camiseta-t12", "BORRADOR", marca, categoria);
    AtributoJpaEntity colorJpa = atributo("Color", "COLOR");
    Atributo color = new Atributo(colorJpa.getId(), "Color", TipoAtributo.COLOR, List.of());

    Variante variante =
        Variante.crear(
            new Sku("TS-CAM-T12-AZ"),
            Dinero.deCop(89_900),
            new BigDecimal("0.19"),
            null,
            new Paquete(180, 30, 25, 4),
            List.of(ValorAtributo.deColor(color, "Azul marino", "#1E3A8A")));

    repositorio.agregarVariante(productoJpa.getId(), variante);

    Optional<Producto> encontrado = repositorio.buscarPorSlug(new Slug("camiseta-t12"));
    assertThat(encontrado).isPresent();
    Producto p = encontrado.orElseThrow();
    assertThat(p.variantes()).hasSize(1);
    assertThat(p.variantes().get(0).sku().valor()).isEqualTo("TS-CAM-T12-AZ");
    assertThat(p.variantes().get(0).atributos()).hasSize(1);
    assertThat(p.variantes().get(0).atributos().get(0).colorHex()).isEqualTo("#1E3A8A");
  }

  /**
   * Una variante sin medir, de ida y vuelta contra Postgres (adr/0046).
   *
   * <p>Esta prueba nació de un 500 en dev, y por eso está: las pruebas de dominio, de caso de uso y
   * de controlador pasaban todas, y aun así guardar una variante sin paquete reventaba. Al cambiar
   * los cuatro campos de la entidad a {@code Integer} se me quedaron sus {@code @Column(nullable =
   * false)}, así que la base ya aceptaba el nulo —la V55 lo permitía— pero Hibernate lo rechazaba
   * antes de llegar a ella. Ninguna capa de arriba puede ver eso; solo una que escriba de verdad.
   */
  @Test
  void agregarVarianteSinMedidasLaPersisteYVuelveVacia() {
    MarcaJpaEntity marca = marca("TecnoSport");
    CategoriaJpaEntity categoria = categoria("Parlantes", "parlantes-t30", "TECNOLOGIA");
    ProductoJpaEntity productoJpa =
        producto("Parlante t30", "parlante-t30", "BORRADOR", marca, categoria);

    Variante sinMedir =
        Variante.crear(
            new Sku("TS-SIN-MEDIR-T30"),
            Dinero.deCop(190_000),
            new BigDecimal("0.00"),
            null,
            null,
            List.of());

    repositorio.agregarVariante(productoJpa.getId(), sinMedir);

    Producto p = repositorio.buscarPorSlug(new Slug("parlante-t30")).orElseThrow();
    assertThat(p.variantes()).hasSize(1);
    assertThat(p.variantes().get(0).paquete()).isEmpty();
  }

  /**
   * La consulta de medidas, contra Postgres de verdad.
   *
   * <p>Siembra las cuatro situaciones que tiene que distinguir —publicada sin medir, borrador sin
   * medir, publicada medida e inactiva— porque lo que hay que demostrar es doble: que trae las
   * activas con y sin paquete, y que la inactiva no entra.
   */
  @Test
  void medidasDeVariantesTraeLasActivasConYSinPaqueteYDiceDeQueProductoSon() {
    MarcaJpaEntity marca = marca("Marca sin medir T1");
    CategoriaJpaEntity categoria = categoria("Parlantes", "parlantes-sm", "TECNOLOGIA");
    ProductoJpaEntity publicado =
        producto("Moto G17 publicado", "moto-g17-sm", "PUBLICADO", marca, categoria);
    ProductoJpaEntity borrador =
        producto("Honor X9d borrador", "honor-x9d-sm", "BORRADOR", marca, categoria);

    varianteSinMedir(publicado, "TS-SM-PUB", "ACTIVA");
    varianteSinMedir(borrador, "TS-SM-BOR", "ACTIVA");
    varianteSinMedir(publicado, "TS-SM-INACTIVA", "INACTIVA");
    variante(publicado, "TS-SM-MEDIDA", "190000");
    entityManager.flush();

    List<MedidaDeVariante> medidas = repositorio.medidasDeVariantes();

    assertThat(medidas)
        .extracting(MedidaDeVariante::sku)
        .contains("TS-SM-PUB", "TS-SM-BOR", "TS-SM-MEDIDA")
        .doesNotContain("TS-SM-INACTIVA");
    MedidaDeVariante delPublicado =
        medidas.stream().filter(v -> v.sku().equals("TS-SM-PUB")).findFirst().orElseThrow();
    assertThat(delPublicado.nombreProducto()).isEqualTo("Moto G17 publicado");
    assertThat(delPublicado.productoId()).isEqualTo(publicado.getId());
    assertThat(delPublicado.estadoProducto()).isEqualTo(EstadoProducto.PUBLICADO);
    assertThat(delPublicado.sinMedir()).isTrue();
    assertThat(delPublicado.medida()).isEmpty();

    // La medida vuelve entera, que es lo que la pantalla de corrección necesita enseñar.
    MedidaDeVariante medida =
        medidas.stream().filter(v -> v.sku().equals("TS-SM-MEDIDA")).findFirst().orElseThrow();
    assertThat(medida.sinMedir()).isFalse();
    assertThat(medida.medida()).contains(new Paquete(180, 30, 25, 4));
  }

  /**
   * Medir escribe las cuatro columnas, y la variante vuelve del catálogo con su paquete.
   *
   * <p>Se lee de vuelta con {@code buscarPorSlug} —el camino que usa la vitrina— y no consultando
   * la fila: lo que hay que demostrar no es que el {@code update} corrió, es que lo escrito se lee
   * como un {@link Paquete}. Es la lección del 500 de la V55, donde base y Hibernate no estaban de
   * acuerdo sobre la misma columna.
   */
  @Test
  void actualizarPaqueteMideUnaVarianteQueEstabaSinMedir() {
    MarcaJpaEntity marca = marca("Marca sin medir T2");
    CategoriaJpaEntity categoria = categoria("Celulares", "celulares-sm2", "TECNOLOGIA");
    ProductoJpaEntity productoJpa =
        producto("Moto G17", "moto-g17-medir", "PUBLICADO", marca, categoria);
    VarianteJpaEntity variante = varianteSinMedir(productoJpa, "TS-MEDIR-1", "ACTIVA");
    entityManager.flush();

    repositorio.actualizarPaquete(variante.getId(), new Paquete(430, 17, 9, 5));
    entityManager.flush();
    entityManager.clear();

    Producto p = repositorio.buscarPorSlug(new Slug("moto-g17-medir")).orElseThrow();
    assertThat(p.variantes().get(0).paquete()).contains(new Paquete(430, 17, 9, 5));
    assertThat(repositorio.medidasDeVariantes())
        .filteredOn(v -> v.sku().equals("TS-MEDIR-1"))
        .singleElement()
        .satisfies(v -> assertThat(v.medida()).contains(new Paquete(430, 17, 9, 5)));
  }

  /** Y remedir reemplaza, que es lo que hace de esto una corrección y no solo un relleno. */
  @Test
  void actualizarPaqueteReemplazaUnaMedidaAnterior() {
    MarcaJpaEntity marca = marca("Marca sin medir T3");
    CategoriaJpaEntity categoria = categoria("Celulares", "celulares-sm3", "TECNOLOGIA");
    ProductoJpaEntity productoJpa =
        producto("Galaxy A17", "galaxy-a17-remedir", "PUBLICADO", marca, categoria);
    VarianteJpaEntity variante = variante(productoJpa, "TS-REMEDIR-1", "890000");

    repositorio.actualizarPaquete(variante.getId(), new Paquete(500, 20, 12, 7));
    entityManager.flush();
    entityManager.clear();

    Producto p = repositorio.buscarPorSlug(new Slug("galaxy-a17-remedir")).orElseThrow();
    assertThat(p.variantes().get(0).paquete()).contains(new Paquete(500, 20, 12, 7));
    assertThat(p.variantes().get(0).precio()).isEqualTo(Dinero.deCop(890_000));
  }

  /** Y una medida sí vuelve completa, para que la de arriba no pase por no leer nada. */
  @Test
  void agregarVarianteConMedidasLaDevuelveCompleta() {
    MarcaJpaEntity marca = marca("TecnoSport");
    CategoriaJpaEntity categoria = categoria("Parlantes", "parlantes-t31", "TECNOLOGIA");
    ProductoJpaEntity productoJpa =
        producto("Parlante t31", "parlante-t31", "BORRADOR", marca, categoria);

    repositorio.agregarVariante(
        productoJpa.getId(),
        Variante.crear(
            new Sku("TS-MEDIDO-T31"),
            Dinero.deCop(190_000),
            new BigDecimal("0.00"),
            null,
            new Paquete(320, 13, 9, 6),
            List.of()));

    Producto p = repositorio.buscarPorSlug(new Slug("parlante-t31")).orElseThrow();
    assertThat(p.variantes().get(0).paquete()).contains(new Paquete(320, 13, 9, 6));
  }

  @Test
  void existeVarianteConSkuDistingueEntreExistenteEInexistente() {
    MarcaJpaEntity marca = marca("TecnoSport");
    CategoriaJpaEntity categoria = categoria("Bolsos", "bolsos-t13", "BOLSOS");
    ProductoJpaEntity productoJpa =
        producto("Morral t13", "morral-t13", "BORRADOR", marca, categoria);
    variante(productoJpa, "TS-MOR-T13", "150000");

    assertThat(repositorio.existeVarianteConSku(new Sku("TS-MOR-T13"))).isTrue();
    assertThat(repositorio.existeVarianteConSku(new Sku("TS-NO-EXISTE-T13"))).isFalse();
  }

  @Test
  void guardarImagenPrincipalInsertaLaPrimeraYLuegoReemplazaSinDuplicarFila() {
    MarcaJpaEntity marca = marca("TecnoSport");
    CategoriaJpaEntity categoria = categoria("Bolsos", "bolsos-t14", "BOLSOS");
    ProductoJpaEntity productoJpa =
        producto("Morral t14", "morral-t14", "BORRADOR", marca, categoria);
    ImagenProducto primera =
        ImagenProducto.crear(
            TipoImagen.PRINCIPAL,
            0,
            List.of(new VarianteDeImagen(1000, "https://cdn/principal-1.webp", 45_000)),
            null,
            800,
            new HashContenido("%064x".formatted(1)),
            "alt es 1",
            "alt en 1");

    repositorio.guardarImagenPrincipal(productoJpa.getId(), primera);

    assertThat(imagenes.findByProductoIdIn(List.of(productoJpa.getId()))).hasSize(1);

    ImagenProducto segunda =
        ImagenProducto.crear(
            TipoImagen.PRINCIPAL,
            0,
            List.of(new VarianteDeImagen(1200, "https://cdn/principal-2.webp", 60_000)),
            null,
            900,
            new HashContenido("%064x".formatted(2)),
            "alt es 2",
            "alt en 2");

    repositorio.guardarImagenPrincipal(productoJpa.getId(), segunda);

    List<ImagenProductoJpaEntity> imagenesDelProducto =
        imagenes.findByProductoIdIn(List.of(productoJpa.getId()));
    assertThat(imagenesDelProducto).hasSize(1);
    assertThat(imagenesDelProducto.get(0).getHash()).isEqualTo("%064x".formatted(2));
    assertThat(imagenesDelProducto.get(0).getAncho()).isEqualTo(1200);
  }

  @Test
  void guardarImagenDeGaleriaAcumulaEnVezDeReemplazarYQuedaLegibleAlHidratar() {
    MarcaJpaEntity marca = marca("TecnoSport");
    CategoriaJpaEntity categoria = categoria("Bolsos", "bolsos-t20", "BOLSOS");
    ProductoJpaEntity productoJpa =
        producto("Morral t20", "morral-t20", "BORRADOR", marca, categoria);
    imagenPrincipal(productoJpa);

    repositorio.guardarImagenDeGaleria(productoJpa.getId(), imagenDeGaleria(0, 1));
    repositorio.guardarImagenDeGaleria(productoJpa.getId(), imagenDeGaleria(1, 2));
    repositorio.guardarImagenDeGaleria(productoJpa.getId(), imagenDeGaleria(2, 3));

    // Lo contrario de la principal, que tiene índice único y reemplaza: aquí caben todas.
    Producto hidratado = repositorio.buscarPorSlug(new Slug("morral-t20")).orElseThrow();
    assertThat(hidratado.galeria()).hasSize(3);
    assertThat(hidratado.galeria().stream().map(ImagenProducto::orden).toList())
        .containsExactly(0, 1, 2);
    assertThat(hidratado.imagenPrincipal()).isPresent();
  }

  @Test
  void laGaleriaSeHidrataOrdenadaAunqueLasFilasSeHayanInsertadoAlReves() {
    MarcaJpaEntity marca = marca("TecnoSport");
    CategoriaJpaEntity categoria = categoria("Bolsos", "bolsos-t21", "BOLSOS");
    ProductoJpaEntity productoJpa =
        producto("Morral t21", "morral-t21", "BORRADOR", marca, categoria);

    repositorio.guardarImagenDeGaleria(productoJpa.getId(), imagenDeGaleria(2, 30));
    repositorio.guardarImagenDeGaleria(productoJpa.getId(), imagenDeGaleria(0, 10));
    repositorio.guardarImagenDeGaleria(productoJpa.getId(), imagenDeGaleria(1, 20));

    Producto hidratado = repositorio.buscarPorSlug(new Slug("morral-t21")).orElseThrow();
    assertThat(hidratado.galeria().stream().map(ImagenProducto::orden).toList())
        .containsExactly(0, 1, 2);
  }

  @Test
  void eliminarImagenDeGaleriaBorraSoloEsaFila() {
    MarcaJpaEntity marca = marca("TecnoSport");
    CategoriaJpaEntity categoria = categoria("Bolsos", "bolsos-t22", "BOLSOS");
    ProductoJpaEntity productoJpa =
        producto("Morral t22", "morral-t22", "BORRADOR", marca, categoria);
    ImagenProducto primera = imagenDeGaleria(0, 11);
    ImagenProducto segunda = imagenDeGaleria(1, 12);
    repositorio.guardarImagenDeGaleria(productoJpa.getId(), primera);
    repositorio.guardarImagenDeGaleria(productoJpa.getId(), segunda);

    repositorio.eliminarImagenDeGaleria(productoJpa.getId(), primera.id());

    List<ImagenProductoJpaEntity> quedan =
        imagenes.findByProductoIdIn(List.of(productoJpa.getId()));
    assertThat(quedan).hasSize(1);
    assertThat(quedan.get(0).getId()).isEqualTo(segunda.id());
  }

  @Test
  void eliminarImagenDeGaleriaNoBorraLaDeOtroProducto() {
    MarcaJpaEntity marca = marca("TecnoSport");
    CategoriaJpaEntity categoria = categoria("Bolsos", "bolsos-t23", "BOLSOS");
    ProductoJpaEntity uno = producto("Morral t23", "morral-t23", "BORRADOR", marca, categoria);
    ProductoJpaEntity otro = producto("Morral t24", "morral-t24", "BORRADOR", marca, categoria);
    ImagenProducto delOtro = imagenDeGaleria(0, 13);
    repositorio.guardarImagenDeGaleria(otro.getId(), delOtro);

    // El id existe, pero no es de este producto: la consulta va por los dos campos a propósito.
    repositorio.eliminarImagenDeGaleria(uno.getId(), delOtro.id());

    assertThat(imagenes.findByProductoIdIn(List.of(otro.getId()))).hasSize(1);
  }

  @Test
  void guardarOrdenDeGaleriaCambiaElOrdenDeLasFilasQueYaEstaban() {
    MarcaJpaEntity marca = marca("TecnoSport");
    CategoriaJpaEntity categoria = categoria("Bolsos", "bolsos-t25", "BOLSOS");
    ProductoJpaEntity productoJpa =
        producto("Morral t25", "morral-t25", "BORRADOR", marca, categoria);
    ImagenProducto primera = imagenDeGaleria(0, 21);
    ImagenProducto segunda = imagenDeGaleria(1, 22);
    ImagenProducto tercera = imagenDeGaleria(2, 23);
    repositorio.guardarImagenDeGaleria(productoJpa.getId(), primera);
    repositorio.guardarImagenDeGaleria(productoJpa.getId(), segunda);
    repositorio.guardarImagenDeGaleria(productoJpa.getId(), tercera);

    Producto hidratado = repositorio.buscarPorSlug(new Slug("morral-t25")).orElseThrow();
    hidratado.reordenarGaleria(List.of(tercera.id(), primera.id(), segunda.id()));
    repositorio.guardarOrdenDeGaleria(hidratado.id(), hidratado.galeria());

    Producto vueltoALeer = repositorio.buscarPorSlug(new Slug("morral-t25")).orElseThrow();
    assertThat(vueltoALeer.galeria().stream().map(ImagenProducto::id).toList())
        .containsExactly(tercera.id(), primera.id(), segunda.id());
    assertThat(vueltoALeer.galeria().stream().map(ImagenProducto::orden).toList())
        .containsExactly(0, 1, 2);
    // Sigue habiendo tres filas: se actualizan, no se borran y se vuelven a insertar.
    assertThat(imagenes.findByProductoIdIn(List.of(productoJpa.getId()))).hasSize(3);
  }

  /**
   * La razón por la que el adaptador modifica la fila en vez de volver a guardarla entera. Un
   * {@code save} con una entidad nueva del mismo id también actualizaría, pero obligaría a rellenar
   * {@code creada_en} —que el dominio no conoce— y dejaría toda la galería como recién creada cada
   * vez que alguien mueve una foto de sitio.
   */
  @Test
  void reordenarNoTocaLaFechaDeCreacionDeLasImagenes() {
    MarcaJpaEntity marca = marca("TecnoSport");
    CategoriaJpaEntity categoria = categoria("Bolsos", "bolsos-t26", "BOLSOS");
    ProductoJpaEntity productoJpa =
        producto("Morral t26", "morral-t26", "BORRADOR", marca, categoria);
    ImagenProducto primera = imagenDeGaleria(0, 24);
    ImagenProducto segunda = imagenDeGaleria(1, 25);
    repositorio.guardarImagenDeGaleria(productoJpa.getId(), primera);
    repositorio.guardarImagenDeGaleria(productoJpa.getId(), segunda);
    Map<UUID, Instant> creadasAntes =
        imagenes.findByProductoIdIn(List.of(productoJpa.getId())).stream()
            .collect(
                Collectors.toMap(
                    ImagenProductoJpaEntity::getId, ImagenProductoJpaEntity::getCreadaEn));

    Producto hidratado = repositorio.buscarPorSlug(new Slug("morral-t26")).orElseThrow();
    hidratado.reordenarGaleria(List.of(segunda.id(), primera.id()));
    repositorio.guardarOrdenDeGaleria(hidratado.id(), hidratado.galeria());

    for (ImagenProductoJpaEntity fila : imagenes.findByProductoIdIn(List.of(productoJpa.getId()))) {
      assertThat(fila.getCreadaEn()).isEqualTo(creadasAntes.get(fila.getId()));
    }
  }

  private ImagenProducto imagenDeGaleria(int orden, int semillaDelHash) {
    String url = "https://cdn/galeria-" + semillaDelHash + ".jpg";
    return ImagenProducto.crear(
        TipoImagen.GALERIA,
        orden,
        List.of(new VarianteDeImagen(2000, url, 120_000)),
        null,
        2000,
        new HashContenido("%064x".formatted(semillaDelHash)),
        "alt es",
        "alt en");
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

  /**
   * El otro recorrido del catálogo por una columna concreta. Se siembran las mismas cuatro
   * situaciones que el de sin-medir, porque una consulta sin el {@code where} también pasaría una
   * prueba que solo sembrara el caso positivo.
   */
  @Test
  void variantesActivasTraeSoloLasActivasConLaExistenciaQueDeclaraElCatalogo() {
    MarcaJpaEntity marca = marca("Marca existencias T1");
    CategoriaJpaEntity categoria = categoria("Parlantes", "parlantes-ex", "TECNOLOGIA");
    ProductoJpaEntity publicado =
        producto("JBL Go 5 publicado", "jbl-go5-ex", "PUBLICADO", marca, categoria);
    ProductoJpaEntity borrador =
        producto("JBL Xtreme borrador", "jbl-xtreme-ex", "BORRADOR", marca, categoria);

    VarianteJpaEntity activaPublicada = variante(publicado, "TS-EX-PUB", "289000");
    variante(borrador, "TS-EX-BOR", "990000");
    varianteSinMedir(publicado, "TS-EX-INACTIVA", "INACTIVA");
    entityManager.flush();

    List<VarianteActiva> activas = repositorio.variantesActivas();

    assertThat(activas)
        .extracting(VarianteActiva::sku)
        .contains("TS-EX-PUB", "TS-EX-BOR")
        .doesNotContain("TS-EX-INACTIVA");
    VarianteActiva delPublicado =
        activas.stream().filter(v -> v.sku().equals("TS-EX-PUB")).findFirst().orElseThrow();
    assertThat(delPublicado.varianteId()).isEqualTo(activaPublicada.getId());
    assertThat(delPublicado.productoId()).isEqualTo(publicado.getId());
    assertThat(delPublicado.nombreProducto()).isEqualTo("JBL Go 5 publicado");
    assertThat(delPublicado.estadoProducto()).isEqualTo(EstadoProducto.PUBLICADO);
  }

  private VarianteJpaEntity variante(ProductoJpaEntity producto, String sku, String precio) {
    return variantes.save(
        new VarianteJpaEntity(
            UUID.randomUUID(),
            producto.getId(),
            sku,
            new BigDecimal(precio),
            new BigDecimal("0.19"),
            null,
            180,
            30,
            25,
            4,
            "ACTIVA",
            Instant.now()));
  }

  private VarianteJpaEntity varianteSinMedir(
      ProductoJpaEntity producto, String sku, String estado) {
    return variantes.save(
        new VarianteJpaEntity(
            UUID.randomUUID(),
            producto.getId(),
            sku,
            new BigDecimal("190000"),
            new BigDecimal("0.00"),
            null,
            null,
            null,
            null,
            null,
            estado,
            Instant.now()));
  }

  private void imagenPrincipal(ProductoJpaEntity producto) {
    String url = "https://picsum.photos/seed/" + producto.getSlug() + "/800/600";
    UUID imagenId = UUID.randomUUID();
    imagenes.save(
        new ImagenProductoJpaEntity(
            imagenId,
            producto.getId(),
            null,
            null,
            "PRINCIPAL",
            0,
            url,
            null,
            800,
            600,
            1000,
            hashDePrueba(producto.getSlug()),
            "alt es",
            "alt en",
            Instant.now()));
    variantesDeImagen.save(
        new VarianteImagenJpaEntity(UUID.randomUUID(), imagenId, 800, url, 1000));
  }

  private void fotogramaRotacion(ProductoJpaEntity producto, SetRotacionJpaEntity set, int orden) {
    String url = "https://picsum.photos/seed/" + producto.getSlug() + "-" + orden + "/800/600";
    UUID imagenId = UUID.randomUUID();
    imagenes.save(
        new ImagenProductoJpaEntity(
            imagenId,
            producto.getId(),
            null,
            set.getId(),
            "ROTACION",
            orden,
            url,
            null,
            800,
            600,
            1000,
            hashDePrueba(producto.getSlug() + "-" + orden),
            "",
            "",
            Instant.now()));
    variantesDeImagen.save(
        new VarianteImagenJpaEntity(UUID.randomUUID(), imagenId, 800, url, 1000));
  }

  /**
   * Un hash con la forma que exige {@code HashContenido} —64 hexadecimales— derivado de la semilla,
   * para que cada imagen de prueba tenga el suyo y siga siendo estable entre corridas.
   */
  private static String hashDePrueba(String semilla) {
    return "%064x".formatted(Integer.toUnsignedLong(semilla.hashCode()));
  }
}
