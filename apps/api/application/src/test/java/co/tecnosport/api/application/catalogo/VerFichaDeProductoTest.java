package co.tecnosport.api.application.catalogo;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.application.compartido.RelojFalso;
import co.tecnosport.api.application.inventario.DisponibilidadDeVariantes;
import co.tecnosport.api.domain.catalogo.Categoria;
import co.tecnosport.api.domain.catalogo.ImagenProducto;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.catalogo.Marca;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.catalogo.TipoImagen;
import co.tecnosport.api.domain.catalogo.Variante;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.HashContenido;
import co.tecnosport.api.domain.compartido.Sku;
import co.tecnosport.api.domain.compartido.Slug;
import co.tecnosport.api.domain.inventario.Inventario;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class VerFichaDeProductoTest {

  private static final Instant AHORA = Instant.parse("2026-09-20T15:00:00Z");

  private final RepositorioInventarioFalso inventarios = new RepositorioInventarioFalso();

  private VerFichaDeProducto verFicha(RepositorioProductosFalso repositorio) {
    return new VerFichaDeProducto(
        repositorio, new DisponibilidadDeVariantes(inventarios, new RelojFalso(AHORA)));
  }

  @Test
  void devuelveElProductoPublicado() {
    RepositorioProductosFalso repositorio = new RepositorioProductosFalso();
    Producto producto = productoPublicado();
    repositorio.conProductos(producto);

    FichaDeProducto ficha =
        verFicha(repositorio).ejecutar(new VerFichaDeProductoComando(producto.slug()));

    assertSame(producto, ficha.producto());
  }

  /**
   * La ficha es donde se habilita el botón de comprar, así que aquí el dato tiene consecuencia
   * directa: con la única unidad reservada por un pedido en vuelo, la variante no está disponible.
   */
  @Test
  void laDisponibilidadDeLaFichaSaleDelLibro() {
    RepositorioProductosFalso repositorio = new RepositorioProductosFalso();
    Producto producto = productoPublicado();
    Variante variante =
        Variante.crear(
            new Sku("TS-CAM-1"),
            Dinero.deCop(80_000),
            new BigDecimal("0.00"),
            null,
            null,
            List.of());
    producto.agregarVariante(variante);
    repositorio.conProductos(producto);
    Inventario libro = Inventario.crear(variante.id());
    libro.registrarEntrada(1, "siembra de prueba", AHORA.minus(Duration.ofDays(1)));
    inventarios.con(libro);

    assertTrue(
        verFicha(repositorio)
            .ejecutar(new VerFichaDeProductoComando(producto.slug()))
            .disponibles()
            .hay(variante.id()));

    libro.reservar(1, Duration.ofMinutes(30), AHORA);

    assertFalse(
        verFicha(repositorio)
            .ejecutar(new VerFichaDeProductoComando(producto.slug()))
            .disponibles()
            .hay(variante.id()));
  }

  @Test
  void unProductoEnBorradorNoSeVePorFuera() {
    RepositorioProductosFalso repositorio = new RepositorioProductosFalso();
    Producto borrador = productoDePrueba();
    repositorio.conProductos(borrador);

    assertThrows(
        ProductoNoEncontradoException.class,
        () -> verFicha(repositorio).ejecutar(new VerFichaDeProductoComando(borrador.slug())));
  }

  @Test
  void unSlugInexistenteLanzaLaMismaExcepcionQueUnBorrador() {
    RepositorioProductosFalso repositorio = new RepositorioProductosFalso();

    assertThrows(
        ProductoNoEncontradoException.class,
        () -> verFicha(repositorio).ejecutar(new VerFichaDeProductoComando(new Slug("no-existe"))));
  }

  private static Producto productoPublicado() {
    Producto producto = productoDePrueba();
    producto.asignarImagenPrincipal(imagenPrincipal());
    producto.publicar();
    return producto;
  }

  private static Producto productoDePrueba() {
    Marca marca = Marca.crear("TecnoSport");
    Categoria categoria =
        Categoria.crear("Ropa deportiva", new Slug("ropa-deportiva"), LineaCatalogo.ROPA_Y_CALZADO);
    return Producto.crear(
        "Camiseta running Dry-Fit", new Slug("camiseta-running-dry-fit"), "", marca, categoria);
  }

  private static ImagenProducto imagenPrincipal() {
    return ImagenProducto.crear(
        TipoImagen.PRINCIPAL,
        0,
        "https://x/0.jpg",
        "https://x/0.webp",
        800,
        600,
        1000,
        new HashContenido("%064x".formatted(0)),
        "alt es",
        "alt en");
  }
}
