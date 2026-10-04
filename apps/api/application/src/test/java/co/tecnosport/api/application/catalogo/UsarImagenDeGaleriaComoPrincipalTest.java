package co.tecnosport.api.application.catalogo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import co.tecnosport.api.domain.catalogo.Categoria;
import co.tecnosport.api.domain.catalogo.ImagenDeGaleriaNoEncontradaException;
import co.tecnosport.api.domain.catalogo.ImagenProducto;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.catalogo.Marca;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.catalogo.TipoImagen;
import co.tecnosport.api.domain.catalogo.VarianteDeImagen;
import co.tecnosport.api.domain.compartido.HashContenido;
import co.tecnosport.api.domain.compartido.Slug;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UsarImagenDeGaleriaComoPrincipalTest {

  private final RepositorioProductosFalso repositorioProductos = new RepositorioProductosFalso();
  private final UsarImagenDeGaleriaComoPrincipal usarComoPrincipal =
      new UsarImagenDeGaleriaComoPrincipal(repositorioProductos);

  private static Producto productoDePrueba() {
    Marca marca = Marca.crear("JBL");
    Categoria categoria =
        Categoria.crear("Parlantes", new Slug("parlantes"), LineaCatalogo.TECNOLOGIA);
    return Producto.crear("JBL Charge 6", new Slug("jbl-charge-6"), "", marca, categoria);
  }

  private static ImagenProducto imagen(TipoImagen tipo, int semilla, String nombre) {
    return ImagenProducto.crear(
        tipo,
        0,
        List.of(new VarianteDeImagen(2000, "https://x/" + nombre + ".jpg", 120_000)),
        null,
        2000,
        new HashContenido("%064x".formatted(semilla)),
        "alt es",
        "alt en");
  }

  @Test
  void grabaElIntercambioYDevuelveLaPrincipalNueva() {
    Producto producto = productoDePrueba();
    producto.asignarImagenPrincipal(imagen(TipoImagen.PRINCIPAL, 1, "principal"));
    ImagenProducto elegida = imagen(TipoImagen.GALERIA, 2, "lado");
    producto.agregarImagenGaleria(elegida);
    repositorioProductos.conProductos(producto);

    ImagenProducto nueva =
        usarComoPrincipal.ejecutar(
            new UsarImagenDeGaleriaComoPrincipalComando(producto.id(), elegida.id()));

    assertEquals("https://x/lado.jpg", nueva.url());
    assertEquals(nueva, repositorioProductos.intercambioGuardado.nuevaPrincipal());
    assertEquals(elegida.id(), repositorioProductos.intercambioGuardado.imagenDeGaleriaQuitada());
    assertEquals(
        "https://x/principal.jpg",
        repositorioProductos.intercambioGuardado.anteriorEnLaGaleria().orElseThrow().url());
  }

  @Test
  void unProductoQueNoExisteFallaSinGrabarNada() {
    assertThrows(
        ProductoNoEncontradoPorIdException.class,
        () ->
            usarComoPrincipal.ejecutar(
                new UsarImagenDeGaleriaComoPrincipalComando(UUID.randomUUID(), UUID.randomUUID())));
    assertNull(repositorioProductos.intercambioGuardado);
  }

  @Test
  void unaFotoQueNoEsDeLaGaleriaFallaSinGrabarNada() {
    Producto producto = productoDePrueba();
    repositorioProductos.conProductos(producto);

    assertThrows(
        ImagenDeGaleriaNoEncontradaException.class,
        () ->
            usarComoPrincipal.ejecutar(
                new UsarImagenDeGaleriaComoPrincipalComando(producto.id(), UUID.randomUUID())));
    assertNull(repositorioProductos.intercambioGuardado);
  }
}
