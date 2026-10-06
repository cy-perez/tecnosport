package co.tecnosport.api.infrastructure.proveedores;

import static org.assertj.core.api.Assertions.assertThat;

import co.tecnosport.api.application.catalogo.FiltroProductos;
import co.tecnosport.api.application.catalogo.OrdenProductos;
import co.tecnosport.api.application.catalogo.RepositorioMapaDelSitio;
import co.tecnosport.api.domain.catalogo.Categoria;
import co.tecnosport.api.domain.catalogo.EstadoDisponibilidad;
import co.tecnosport.api.domain.catalogo.ImagenProducto;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.catalogo.Marca;
import co.tecnosport.api.domain.catalogo.OrigenProducto;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.catalogo.TipoImagen;
import co.tecnosport.api.domain.catalogo.Variante;
import co.tecnosport.api.domain.catalogo.VarianteDeImagen;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.HashContenido;
import co.tecnosport.api.domain.compartido.Sku;
import co.tecnosport.api.domain.compartido.Slug;
import co.tecnosport.api.domain.proveedores.HuellaProveedor;
import co.tecnosport.api.domain.proveedores.OrdenDePublicacion;
import co.tecnosport.api.domain.proveedores.Proveedor;
import co.tecnosport.api.infrastructure.catalogo.CategoriaJpaRepository;
import co.tecnosport.api.infrastructure.catalogo.MarcaJpaRepository;
import co.tecnosport.api.infrastructure.catalogo.RepositorioProductosJpa;
import co.tecnosport.api.infrastructure.catalogo.entidad.CategoriaJpaEntity;
import co.tecnosport.api.infrastructure.catalogo.entidad.MarcaJpaEntity;
import java.math.BigDecimal;
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

/**
 * El mapeo de las seis columnas nuevas de {@code producto} contra la V71, la huella como llave, la
 * consulta del job, y que el catálogo público deje de listar lo oculto sin dejar de responder su
 * ficha.
 */
@SpringBootTest
@Testcontainers
@Transactional
class RepositorioProductosDeProveedorJpaTest {

  @Container @ServiceConnection
  static PostgreSQLContainer postgres =
      new PostgreSQLContainer(DockerImageName.parse("postgres:16"));

  private static final Instant VISTO = Instant.parse("2026-09-28T15:15:00Z");

  @Autowired private RepositorioProductosJpa productos;
  @Autowired private RepositorioProductosDeProveedorJpa deProveedor;
  @Autowired private RepositorioProveedoresJpa proveedores;
  @Autowired private RepositorioMapaDelSitio mapaDelSitio;
  @Autowired private MarcaJpaRepository marcas;
  @Autowired private CategoriaJpaRepository categorias;

  private Marca marca;
  private Categoria categoria;
  private Proveedor proveedor;

  private void catalogoBase() {
    MarcaJpaEntity m =
        marcas.save(new MarcaJpaEntity(UUID.randomUUID(), "Genérica", Instant.now()));
    CategoriaJpaEntity c =
        categorias.save(
            new CategoriaJpaEntity(
                UUID.randomUUID(),
                "Bolsos de mano",
                "bolsos-de-mano-" + UUID.randomUUID(),
                "BOLSOS",
                null,
                Instant.now()));
    marca = new Marca(m.getId(), m.getNombre());
    categoria =
        new Categoria(
            c.getId(), c.getNombre(), new Slug(c.getSlug()), LineaCatalogo.BOLSOS, null, List.of());
    proveedor =
        Proveedor.crear(
            "Bolsos",
            LineaCatalogo.BOLSOS,
            "+57 300",
            "Bolsos Centro",
            null,
            OrdenDePublicacion.FOTOS_PRIMERO);
    proveedores.guardar(proveedor);
  }

  private Producto deProveedor(String titulo, long precio) {
    return Producto.crearDeProveedor(
        titulo,
        Slug.generarDesde(titulo + "-" + UUID.randomUUID()),
        "",
        marca,
        categoria,
        proveedor.id(),
        Dinero.deCop(precio),
        HuellaProveedor.calcular(proveedor.id(), titulo, Dinero.deCop(precio)),
        VISTO);
  }

  private static ImagenProducto principal(UUID varianteId) {
    return ImagenProducto.crearDeVariante(
        TipoImagen.PRINCIPAL,
        0,
        List.of(new VarianteDeImagen(1200, "https://b/" + UUID.randomUUID() + ".jpg", 900)),
        null,
        1500,
        new HashContenido(
            "%064x".formatted(UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE)),
        "Bolso",
        "Bag",
        varianteId);
  }

  @Test
  void elProductoDeProveedorVaYVuelveConSusSeisColumnasYElManualSinEllas() {
    catalogoBase();
    Producto producto = deProveedor("Bolso de dama mediano", 53000);
    productos.guardar(producto);
    Producto manual =
        Producto.crear("Camiseta", new Slug("camiseta-" + UUID.randomUUID()), "", marca, categoria);
    productos.guardar(manual);

    Producto leido = productos.buscarPorId(producto.id()).orElseThrow();
    assertThat(leido.origen()).isEqualTo(OrigenProducto.PROVEEDOR);
    assertThat(leido.proveedorId()).contains(proveedor.id());
    assertThat(leido.precioProveedor()).contains(Dinero.deCop(53000));
    assertThat(leido.huellaProveedor()).isEqualTo(producto.huellaProveedor());
    assertThat(leido.vistoPorUltimaVez()).contains(VISTO);
    assertThat(leido.estadoDisponibilidad()).isEqualTo(EstadoDisponibilidad.DISPONIBLE);

    Producto leidoManual = productos.buscarPorId(manual.id()).orElseThrow();
    assertThat(leidoManual.origen()).isEqualTo(OrigenProducto.MANUAL);
    assertThat(leidoManual.proveedorId()).isEmpty();
    assertThat(leidoManual.huellaProveedor()).isEmpty();
  }

  @Test
  void laHuellaEncuentraElProductoDelProveedorYSoloDeEl() {
    catalogoBase();
    Producto producto = deProveedor("Bolso de dama mediano", 53000);
    productos.guardar(producto);
    HuellaProveedor huella = producto.huellaProveedor().orElseThrow();

    assertThat(deProveedor.buscarPorHuella(proveedor.id(), huella))
        .map(Producto::id)
        .contains(producto.id());
    assertThat(deProveedor.buscarPorHuella(UUID.randomUUID(), huella)).isEmpty();
    assertThat(
            deProveedor.buscarPorHuella(
                proveedor.id(),
                HuellaProveedor.calcular(proveedor.id(), "Otro", Dinero.deCop(1000))))
        .isEmpty();
  }

  @Test
  void laConsultaDelJobTraeSoloLosDeProveedorDisponiblesVistosAntesDelLimite() {
    catalogoBase();
    Producto viejo = deProveedor("Viejo", 1000);
    Producto reciente = deProveedor("Reciente", 2000);
    reciente.renovar(VISTO.plusSeconds(3600));
    Producto yaOculto = deProveedor("Oculto", 3000);
    yaOculto.ocultarPorVencimiento();
    Producto manual =
        Producto.crear("Manual", new Slug("manual-" + UUID.randomUUID()), "", marca, categoria);
    for (Producto p : List.of(viejo, reciente, yaOculto, manual)) {
      productos.guardar(p);
    }

    List<Producto> vencidos = deProveedor.disponiblesVistosAntesDe(VISTO.plusSeconds(1800));

    assertThat(vencidos).extracting(Producto::id).containsExactly(viejo.id());
  }

  @Test
  void elCatalogoPublicoNoListaLoOcultoPeroSuFichaSigueRespondiendo() {
    catalogoBase();
    Producto visible = deProveedor("Visible", 53000);
    Producto oculto = deProveedor("Oculto", 60000);
    for (Producto p : List.of(visible, oculto)) {
      p.agregarVariante(
          Variante.crear(
              new Sku("SKU-" + UUID.randomUUID()),
              Dinero.deCop(70000),
              BigDecimal.ZERO,
              null,
              null,
              List.of()));
      productos.guardar(p);
      productos.agregarVariante(p.id(), p.variantes().get(0));
      ImagenProducto imagen = principal(p.variantes().get(0).id());
      p.asignarImagenPrincipal(imagen);
      productos.guardarImagenPrincipal(p.id(), imagen);
      p.publicar();
      productos.actualizar(p);
    }
    oculto.ocultarPorVencimiento();
    productos.actualizar(oculto);

    List<UUID> listados =
        productos
            .buscar(FiltroProductos.vacio(), OrdenProductos.RELEVANCIA, null, 50)
            .items()
            .stream()
            .map(Producto::id)
            .toList();
    assertThat(listados).contains(visible.id()).doesNotContain(oculto.id());
    assertThat(mapaDelSitio.listarProductosPublicados(100))
        .extracting(e -> e.slug().valor())
        .contains(visible.slug().valor())
        .doesNotContain(oculto.slug().valor());

    Producto ficha = productos.buscarPorSlug(oculto.slug()).orElseThrow();
    assertThat(ficha.estadoDisponibilidad()).isEqualTo(EstadoDisponibilidad.OCULTO_POR_VENCIMIENTO);
    assertThat(ficha.imagenPrincipal().orElseThrow().varianteId())
        .as("la foto cuelga de su variante")
        .contains(oculto.variantes().get(0).id());
  }

  /** Un producto basta para que el proveedor no se pueda eliminar; los manuales no cuentan. */
  @Test
  void lasDependenciasCuentanSoloLosProductosDelProveedor() {
    catalogoBase();
    assertThat(proveedores.dependenciasDe(proveedor.id()).productos()).isZero();

    productos.guardar(deProveedor("Bolso de dama mediano", 53000));
    productos.guardar(deProveedor("Morral dúo", 60000));

    assertThat(proveedores.dependenciasDe(proveedor.id()).productos()).isEqualTo(2);
  }

  /** La columna de V79 va y vuelve, al guardar y al actualizar. */
  @Test
  void lasFotosGeneralesEnCadaColorVanYVuelven() {
    catalogoBase();
    Producto producto = deProveedor("Bolso de dama mediano", 53000);
    producto.definirFotosGeneralesEnCadaColor(false);
    productos.guardar(producto);

    Producto leido = productos.buscarPorId(producto.id()).orElseThrow();
    assertThat(leido.fotosGeneralesEnCadaColor()).isFalse();

    leido.definirFotosGeneralesEnCadaColor(true);
    productos.actualizar(leido);
    assertThat(productos.buscarPorId(producto.id()).orElseThrow().fotosGeneralesEnCadaColor())
        .isTrue();
  }
}
