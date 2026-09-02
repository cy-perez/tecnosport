package co.tecnosport.api.domain.catalogo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.Sku;
import co.tecnosport.api.domain.compartido.Slug;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProductoTest {

  @Test
  void noSePublicaSinImagenPrincipal() {
    Producto producto = productoDePrueba();

    assertThrows(ProductoSinImagenPrincipalException.class, producto::publicar);
    assertEquals(EstadoProducto.BORRADOR, producto.estado());
  }

  @Test
  void sePublicaConImagenPrincipal() {
    Producto producto = productoDePrueba();
    producto.asignarImagenPrincipal(imagenPrincipal());

    producto.publicar();

    assertEquals(EstadoProducto.PUBLICADO, producto.estado());
  }

  @Test
  void rechazaSkuDuplicadoEnElMismoProducto() {
    Producto producto = productoDePrueba();
    producto.agregarVariante(variante("TS-CAM-AZ-M"));

    assertThrows(
        SkuDuplicadoException.class, () -> producto.agregarVariante(variante("TS-CAM-AZ-M")));
  }

  @Test
  void rechazaImagenPrincipalDeOtroTipo() {
    Producto producto = productoDePrueba();
    ImagenProducto galeria =
        ImagenProducto.crear(
            TipoImagen.GALERIA,
            0,
            "https://x/1.jpg",
            "https://x/1.webp",
            800,
            600,
            1000,
            "h1",
            "alt",
            "alt");

    assertThrows(
        ImagenProductoInvalidaException.class, () -> producto.asignarImagenPrincipal(galeria));
  }

  private static Producto productoDePrueba() {
    Marca marca = Marca.crear("TecnoSport");
    Categoria categoria =
        Categoria.crear("Ropa deportiva", new Slug("ropa-deportiva"), LineaCatalogo.ROPA_Y_CALZADO);
    return Producto.crear(
        "Camiseta running Dry-Fit",
        new Slug("camiseta-running-dry-fit"),
        "Descripción",
        marca,
        categoria);
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
        "h0",
        "alt es",
        "alt en");
  }

  private static Variante variante(String sku) {
    return Variante.crear(
        new Sku(sku), Dinero.deCop(89_900), new BigDecimal("0.19"), 5, null, List.of());
  }
}
