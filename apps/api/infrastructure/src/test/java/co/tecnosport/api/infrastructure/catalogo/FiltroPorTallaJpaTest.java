package co.tecnosport.api.infrastructure.catalogo;

import static org.assertj.core.api.Assertions.assertThat;

import co.tecnosport.api.application.catalogo.FiltroDeTalla;
import co.tecnosport.api.application.catalogo.FiltroProductos;
import co.tecnosport.api.application.catalogo.OrdenProductos;
import co.tecnosport.api.application.catalogo.TallaEnExistencia;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.compartido.Slug;
import co.tecnosport.api.domain.inventario.Inventario;
import co.tecnosport.api.domain.inventario.MovimientoInventario;
import co.tecnosport.api.infrastructure.catalogo.entidad.AtributoJpaEntity;
import co.tecnosport.api.infrastructure.catalogo.entidad.CategoriaJpaEntity;
import co.tecnosport.api.infrastructure.catalogo.entidad.ImagenProductoJpaEntity;
import co.tecnosport.api.infrastructure.catalogo.entidad.MarcaJpaEntity;
import co.tecnosport.api.infrastructure.catalogo.entidad.ProductoJpaEntity;
import co.tecnosport.api.infrastructure.catalogo.entidad.VarianteAtributoValorJpaEntity;
import co.tecnosport.api.infrastructure.catalogo.entidad.VarianteImagenJpaEntity;
import co.tecnosport.api.infrastructure.catalogo.entidad.VarianteJpaEntity;
import co.tecnosport.api.infrastructure.inventario.RepositorioInventarioJpa;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
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
 * El filtro de talla de la vitrina, contra Postgres de verdad: la condición de existencia es SQL
 * ({@code SaldoDisponibleSql}) y un doble de prueba no la ejecutaría.
 *
 * <p>La prueba que importa es {@link #elSqlDiceLoMismoQueElDominioSobreCadaLibro}: arma los libros
 * de inventario con el dominio, con cada forma que tiene una reserva de seguir o dejar de estar en
 * pie, y exige que la consulta deje pasar exactamente los que {@code Inventario.saldoDisponible} da
 * por vendibles. Es lo que avisa el día que una de las dos versiones de la regla cambie sola.
 */
@SpringBootTest
@Testcontainers
@Transactional
class FiltroPorTallaJpaTest {

  @Container @ServiceConnection
  static PostgreSQLContainer postgres =
      new PostgreSQLContainer(DockerImageName.parse("postgres:16"));

  @Autowired private RepositorioProductosJpa repositorio;
  @Autowired private TallasDelCatalogoJdbc tallas;
  @Autowired private RepositorioInventarioJpa inventarios;
  @Autowired private MarcaJpaRepository marcas;
  @Autowired private CategoriaJpaRepository categorias;
  @Autowired private AtributoJpaRepository atributos;
  @Autowired private ProductoJpaRepository productos;
  @Autowired private VarianteJpaRepository variantes;
  @Autowired private VarianteAtributoValorJpaRepository valoresAtributo;
  @Autowired private ImagenProductoJpaRepository imagenes;
  @Autowired private VarianteImagenJpaRepository variantesDeImagen;
  @Autowired private jakarta.persistence.EntityManager entityManager;

  private MarcaJpaEntity marca;
  private CategoriaJpaEntity ropa;
  private CategoriaJpaEntity calzado;
  private AtributoJpaEntity talla;
  private AtributoJpaEntity tallaCalzado;

  @BeforeEach
  void sembrarElCatalogo() {
    marca = marcas.save(new MarcaJpaEntity(UUID.randomUUID(), "Marca talla", Instant.now()));
    ropa = categoria("Camisetas", "ropa-filtro-talla", "ROPA");
    calzado = categoria("Deportivo", "calzado-filtro-talla", "CALZADO");
    // Nombres propios y no «Talla»: la V72 ya siembra uno con ese nombre. Los dos empiezan por
    // «Talla», que es lo que los vuelve eje de talla (`Atributo.esTalla`).
    talla = atributo("Talla filtro", "TEXTO");
    tallaCalzado = atributo("Talla calzado filtro", "TEXTO");
  }

  @Test
  void elSqlDiceLoMismoQueElDominioSobreCadaLibro() {
    Instant ahora = Instant.now();
    Map<String, Consumer<Inventario>> libros = new LinkedHashMap<>();
    libros.put("con-saldo", libro -> libro.registrarEntrada(3, "compra", ahora));
    libros.put(
        "reserva-vigente",
        libro -> {
          libro.registrarEntrada(1, "compra", ahora);
          libro.reservar(1, Duration.ofMinutes(30), ahora);
        });
    libros.put(
        "reserva-vencida",
        libro -> {
          Instant antes = ahora.minus(Duration.ofHours(2));
          libro.registrarEntrada(1, "compra", antes);
          libro.reservar(1, Duration.ofMinutes(30), antes);
        });
    libros.put(
        "contraentrega-sin-vencer",
        libro -> {
          libro.registrarEntrada(1, "compra", ahora);
          libro.reservar(1, null, ahora);
        });
    libros.put(
        "reserva-liberada",
        libro -> {
          libro.registrarEntrada(1, "compra", ahora);
          MovimientoInventario reserva = libro.reservar(1, null, ahora);
          libro.liberar(reserva.id(), "cancelado", ahora);
        });
    libros.put(
        "reserva-vendida",
        libro -> {
          libro.registrarEntrada(1, "compra", ahora);
          MovimientoInventario reserva = libro.reservar(1, Duration.ofMinutes(30), ahora);
          libro.confirmar(reserva.id(), ahora);
        });
    libros.put(
        "una-de-dos-reservada",
        libro -> {
          libro.registrarEntrada(2, "compra", ahora);
          libro.reservar(1, Duration.ofMinutes(30), ahora);
        });
    libros.put(
        "ajustada-a-cero",
        libro -> {
          libro.registrarEntrada(2, "compra", ahora);
          libro.registrarAjuste(-2, "conteo", ahora);
        });

    Set<String> vendiblesSegunElDominio =
        libros.entrySet().stream()
            .filter(
                entrada -> {
                  String slug = "camiseta-" + entrada.getKey();
                  VarianteJpaEntity variante = variante(prenda(slug, ropa), talla, "M", "ACTIVA");
                  Inventario libro = Inventario.crear(variante.getId());
                  entrada.getValue().accept(libro);
                  inventarios.guardar(libro);
                  return libro.saldoDisponible(Instant.now()) > 0;
                })
            .map(entrada -> "camiseta-" + entrada.getKey())
            .collect(Collectors.toSet());
    entityManager.flush();

    // Sin esto la prueba pasaría con un SQL que deja pasar todo o que no deja pasar nada.
    assertThat(vendiblesSegunElDominio)
        .containsExactlyInAnyOrder(
            "camiseta-con-saldo",
            "camiseta-reserva-vencida",
            "camiseta-reserva-liberada",
            "camiseta-una-de-dos-reservada");
    assertThat(slugsCon(new FiltroDeTalla(LineaCatalogo.ROPA, "M")))
        .containsExactlyInAnyOrderElementsOf(vendiblesSegunElDominio);
  }

  /**
   * {@code Variante.seVende}: agotada por el proveedor o inactiva no se vende, tenga saldo o no.
   */
  @Test
  void unaVarianteQueNoSeVendeNoCuentaAunqueTengaSaldo() {
    conSaldo(variante(prenda("camiseta-vendible", ropa), talla, "M", "ACTIVA"), 5);
    VarianteJpaEntity agotada = variante(prenda("camiseta-agotada", ropa), talla, "M", "ACTIVA");
    variantes.save(agotada.conAgotadaPorProveedor(true));
    conSaldo(agotada, 5);
    conSaldo(variante(prenda("camiseta-inactiva", ropa), talla, "M", "INACTIVA"), 5);
    variante(prenda("camiseta-sin-libro", ropa), talla, "M", "ACTIVA");
    entityManager.flush();

    assertThat(slugsCon(new FiltroDeTalla(LineaCatalogo.ROPA, "M")))
        .containsExactly("camiseta-vendible");
  }

  /** «m» y «2XL» como los escribió el proveedor; el filtro llega normalizado. */
  @Test
  void encuentraLaTallaComoQuieraQueEsteEscrita() {
    conSaldo(variante(prenda("camiseta-m-minuscula", ropa), talla, "m", "ACTIVA"), 1);
    conSaldo(variante(prenda("camiseta-2xl", ropa), talla, "2XL", "ACTIVA"), 1);
    conSaldo(variante(prenda("camiseta-s", ropa), talla, "S", "ACTIVA"), 1);
    entityManager.flush();

    assertThat(slugsCon(new FiltroDeTalla(LineaCatalogo.ROPA, "M")))
        .containsExactly("camiseta-m-minuscula");
    assertThat(slugsCon(new FiltroDeTalla(LineaCatalogo.ROPA, "xxl")))
        .containsExactly("camiseta-2xl");
    assertThat(slugsCon(new FiltroDeTalla(LineaCatalogo.ROPA, "XS"))).isEmpty();
  }

  /** Un jean 38 y un tenis 38: cada desplegable trae solo lo de su línea. */
  @Test
  void elMismoNumeroEnRopaYEnCalzadoSonDosTallas() {
    conSaldo(variante(prenda("jean-38", ropa), talla, "38", "ACTIVA"), 1);
    conSaldo(variante(prenda("tenis-38", calzado), tallaCalzado, "38", "ACTIVA"), 1);
    entityManager.flush();

    assertThat(slugsCon(new FiltroDeTalla(LineaCatalogo.ROPA, "38"))).containsExactly("jean-38");
    assertThat(slugsCon(new FiltroDeTalla(LineaCatalogo.CALZADO, "38")))
        .containsExactly("tenis-38");
  }

  /** Solo cuenta el eje de talla: una «M» en otro atributo no es una talla M. */
  @Test
  void unValorQueNoEsDeUnEjeDeTallaNoCuenta() {
    AtributoJpaEntity material = atributo("Material filtro", "TEXTO");
    conSaldo(variante(prenda("camiseta-material-m", ropa), material, "M", "ACTIVA"), 1);
    entityManager.flush();

    assertThat(slugsCon(new FiltroDeTalla(LineaCatalogo.ROPA, "M"))).isEmpty();
  }

  @Test
  void lasTallasEnExistenciaSonLasQueSePuedenComprar() {
    conSaldo(variante(prenda("camiseta-unica", ropa), talla, "Única", "ACTIVA"), 1);
    conSaldo(variante(prenda("tenis-41", calzado), tallaCalzado, "41", "ACTIVA"), 1);
    conSaldo(variante(prenda("camiseta-4xl-inactiva", ropa), talla, "4XL", "INACTIVA"), 1);
    variante(prenda("camiseta-5xl-sin-saldo", ropa), talla, "5XL", "ACTIVA");
    ProductoJpaEntity borrador = producto("camiseta-6xl-borrador", ropa, "BORRADOR");
    conSaldo(variante(borrador, talla, "6XL", "ACTIVA"), 1);
    entityManager.flush();

    List<TallaEnExistencia> enExistencia = tallas.tallasEnExistencia();

    assertThat(enExistencia)
        .contains(
            new TallaEnExistencia(LineaCatalogo.ROPA, "Única"),
            new TallaEnExistencia(LineaCatalogo.CALZADO, "41"))
        .extracting(TallaEnExistencia::valor)
        .doesNotContain("4XL", "5XL", "6XL");
  }

  private List<String> slugsCon(FiltroDeTalla filtroDeTalla) {
    FiltroProductos filtro = new FiltroProductos(null, null, null, null, null, null, filtroDeTalla);
    return repositorio.buscar(filtro, OrdenProductos.MAS_RECIENTES, null, 50).items().stream()
        .map(Producto::slug)
        .map(Slug::valor)
        .toList();
  }

  private void conSaldo(VarianteJpaEntity variante, int unidades) {
    Inventario libro = Inventario.crear(variante.getId());
    libro.registrarEntrada(unidades, "compra", Instant.now());
    inventarios.guardar(libro);
  }

  private ProductoJpaEntity prenda(String slug, CategoriaJpaEntity categoria) {
    return producto(slug, categoria, "PUBLICADO");
  }

  private ProductoJpaEntity producto(String slug, CategoriaJpaEntity categoria, String estado) {
    Instant ahora = Instant.now();
    ProductoJpaEntity producto =
        productos.save(
            new ProductoJpaEntity(
                UUID.randomUUID(),
                "Producto " + slug,
                slug,
                "",
                marca.getId(),
                categoria.getId(),
                estado,
                ahora,
                ahora));
    imagenPrincipal(producto);
    return producto;
  }

  private VarianteJpaEntity variante(
      ProductoJpaEntity producto, AtributoJpaEntity eje, String valor, String estado) {
    VarianteJpaEntity variante =
        variantes.save(
            new VarianteJpaEntity(
                UUID.randomUUID(),
                producto.getId(),
                "SKU-" + producto.getSlug(),
                new BigDecimal("90000"),
                new BigDecimal("90000"),
                new BigDecimal("0.19"),
                null,
                180,
                30,
                25,
                4,
                estado,
                Instant.now()));
    valoresAtributo.save(
        new VarianteAtributoValorJpaEntity(
            UUID.randomUUID(), variante.getId(), eje.getId(), valor, null));
    return variante;
  }

  private CategoriaJpaEntity categoria(String nombre, String slug, String linea) {
    return categorias.save(
        new CategoriaJpaEntity(UUID.randomUUID(), nombre, slug, linea, null, Instant.now()));
  }

  private AtributoJpaEntity atributo(String nombre, String tipo) {
    return atributos.save(
        new AtributoJpaEntity(UUID.randomUUID(), nombre, tipo, List.of(), Instant.now()));
  }

  private void imagenPrincipal(ProductoJpaEntity producto) {
    String url = "https://picsum.photos/seed/" + producto.getSlug() + "/800/600";
    UUID imagenId = UUID.randomUUID();
    imagenes.save(
        new ImagenProductoJpaEntity(
            imagenId,
            producto.getId(),
            null,
            "PRINCIPAL",
            0,
            url,
            null,
            800,
            600,
            1000,
            "%064x".formatted(Integer.toUnsignedLong(producto.getSlug().hashCode())),
            "alt es",
            "alt en",
            Instant.now()));
    variantesDeImagen.save(
        new VarianteImagenJpaEntity(UUID.randomUUID(), imagenId, 800, url, 1000));
  }
}
