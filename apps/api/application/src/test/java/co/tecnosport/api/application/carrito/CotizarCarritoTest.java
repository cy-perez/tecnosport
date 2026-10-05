package co.tecnosport.api.application.carrito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.domain.carrito.Carrito;
import co.tecnosport.api.domain.catalogo.Categoria;
import co.tecnosport.api.domain.catalogo.ImagenProducto;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.catalogo.Marca;
import co.tecnosport.api.domain.catalogo.Paquete;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.catalogo.TipoImagen;
import co.tecnosport.api.domain.catalogo.Variante;
import co.tecnosport.api.domain.catalogo.VarianteDeImagen;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.HashContenido;
import co.tecnosport.api.domain.compartido.Sku;
import co.tecnosport.api.domain.compartido.Slug;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CotizarCarritoTest {

  private static final Instant AHORA = Instant.parse("2026-10-04T12:00:00Z");

  private final RepositorioCarritoFalso carritos = new RepositorioCarritoFalso();
  private final RepositorioProductosFalso productos = new RepositorioProductosFalso();
  private final java.util.List<Producto> sembrados = new java.util.ArrayList<>();

  private CotizarCarrito casoDeUso() {
    return new CotizarCarrito(carritos, productos);
  }

  private Variante publicada(String sku, long precio, boolean publicar) {
    Producto producto =
        Producto.crear(
            "Producto " + sku,
            new Slug("producto-" + sku.toLowerCase()),
            "Descripción",
            Marca.crear("TecnoSport"),
            Categoria.crear("Ropa", new Slug("ropa-" + sku.toLowerCase()), LineaCatalogo.ROPA));
    producto.asignarImagenPrincipal(
        ImagenProducto.crear(
            TipoImagen.PRINCIPAL,
            0,
            List.of(new VarianteDeImagen(800, "https://cdn.tecnosport.co/" + sku + ".jpg", 1000)),
            null,
            600,
            new HashContenido("%064x".formatted(sku.hashCode() & 0xffff)),
            "alt es",
            "alt en"));
    Variante variante =
        Variante.crear(
            new Sku(sku),
            Dinero.deCop(precio),
            BigDecimal.ZERO,
            null,
            new Paquete(150, 28, 22, 3),
            List.of());
    producto.agregarVariante(variante);
    if (publicar) {
      producto.publicar();
    }
    sembrados.add(producto);
    productos.conProductos(sembrados.toArray(Producto[]::new));
    return variante;
  }

  /**
   * El precio sale del catálogo de hoy, no de lo que recordó el navegador: es lo que el comprador
   * tiene que ver antes de pagar.
   */
  @Test
  void cotizaCadaLineaConElPrecioDelCatalogoYSumaElSubtotal() {
    Variante camiseta = publicada("TS-CAM", 50_000, true);
    Variante gorra = publicada("TS-GOR", 30_000, true);
    Carrito carrito = Carrito.crear(null, AHORA);
    carrito.agregarLinea(camiseta.id(), 2, AHORA);
    carrito.agregarLinea(gorra.id(), 1, AHORA);
    carritos.guardar(carrito);

    CarritoCotizado cotizado = casoDeUso().ejecutar(carrito.id());

    assertEquals(Dinero.deCop(130_000), cotizado.subtotal());
    assertEquals(Dinero.deCop(50_000), cotizado.lineas().get(0).precioUnitario());
    assertEquals(Dinero.deCop(100_000), cotizado.lineas().get(0).subtotal());
  }

  /** Una variante que ya no se vende no suma: el pedido la rechazaría igual. */
  @Test
  void unaVarianteQueYaNoSeVendeNoSumaYSeMarca() {
    Variante viva = publicada("TS-VIV", 50_000, true);
    Variante retirada = publicada("TS-RET", 99_000, false);
    Carrito carrito = Carrito.crear(null, AHORA);
    carrito.agregarLinea(viva.id(), 1, AHORA);
    carrito.agregarLinea(retirada.id(), 1, AHORA);
    carritos.guardar(carrito);

    CarritoCotizado cotizado = casoDeUso().ejecutar(carrito.id());

    assertEquals(Dinero.deCop(50_000), cotizado.subtotal());
    CarritoCotizado.LineaCotizada linea = cotizado.lineas().get(1);
    assertFalse(linea.disponible());
    assertNull(linea.subtotal());
    assertTrue(cotizado.lineas().get(0).disponible());
  }

  @Test
  void unCarritoQueNoExisteSeRechaza() {
    assertThrows(CarritoNoEncontradoException.class, () -> casoDeUso().ejecutar(UUID.randomUUID()));
  }
}
