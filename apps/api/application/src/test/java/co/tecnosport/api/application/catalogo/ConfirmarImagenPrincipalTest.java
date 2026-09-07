package co.tecnosport.api.application.catalogo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import co.tecnosport.api.domain.catalogo.Categoria;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.catalogo.Marca;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.catalogo.TipoImagen;
import co.tecnosport.api.domain.compartido.Slug;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ConfirmarImagenPrincipalTest {

  /** El SHA-256 que el panel calcula en el navegador sobre los bytes que subió. */
  private static final String HASH =
      "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

  private final RepositorioProductosFalso repositorioProductos = new RepositorioProductosFalso();
  private final AlmacenDeImagenesFalso almacenDeImagenes = new AlmacenDeImagenesFalso();
  private final ConfirmarImagenPrincipal confirmarImagenPrincipal =
      new ConfirmarImagenPrincipal(repositorioProductos, almacenDeImagenes);

  private Producto productoDePrueba() {
    Marca marca = Marca.crear("TecnoSport");
    Categoria categoria = Categoria.crear("Bolsos", new Slug("bolsos"), LineaCatalogo.BOLSOS);
    return Producto.crear("Morral urbano", new Slug("morral-urbano"), "", marca, categoria);
  }

  @Test
  void confirmaLaImagenYReemplazaLaPrincipalDelProducto() {
    Producto producto = productoDePrueba();
    repositorioProductos.conProductos(producto);
    String objectKey = "productos/" + producto.id() + "/principal-abc.webp";
    almacenDeImagenes.conObjeto(objectKey, 45_000);

    var imagen =
        confirmarImagenPrincipal.ejecutar(
            new ConfirmarImagenPrincipalComando(
                producto.id(), objectKey, 1000, 800, HASH, "alt es", "alt en"));

    assertEquals(TipoImagen.PRINCIPAL, imagen.tipo());
    assertEquals(1000, imagen.ancho());
    assertEquals(800, imagen.alto());
    assertEquals(45_000, imagen.bytes());
    assertEquals(HASH, imagen.hash().valor());
    assertEquals(producto.id(), repositorioProductos.ultimoProductoIdConImagen);
    assertEquals(imagen, repositorioProductos.ultimaImagenPrincipal);
  }

  @Test
  void productoInexistenteLanzaProductoNoEncontradoPorId() {
    UUID productoId = UUID.randomUUID();
    String objectKey = "productos/" + productoId + "/principal-abc.webp";
    almacenDeImagenes.conObjeto(objectKey, 1000);

    assertThrows(
        ProductoNoEncontradoPorIdException.class,
        () ->
            confirmarImagenPrincipal.ejecutar(
                new ConfirmarImagenPrincipalComando(
                    productoId, objectKey, 100, 100, HASH, "a", "b")));
  }

  @Test
  void objectKeyDeOtroProductoLanzaIllegalArgument() {
    Producto producto = productoDePrueba();
    repositorioProductos.conProductos(producto);
    String objectKeyDeOtroProducto = "productos/" + UUID.randomUUID() + "/principal-abc.webp";
    almacenDeImagenes.conObjeto(objectKeyDeOtroProducto, 1000);

    assertThrows(
        IllegalArgumentException.class,
        () ->
            confirmarImagenPrincipal.ejecutar(
                new ConfirmarImagenPrincipalComando(
                    producto.id(), objectKeyDeOtroProducto, 100, 100, HASH, "a", "b")));
  }

  @Test
  void objetoInexistenteEnElAlmacenLanzaObjetoDeImagenNoEncontrado() {
    Producto producto = productoDePrueba();
    repositorioProductos.conProductos(producto);
    String objectKey = "productos/" + producto.id() + "/principal-nunca-subido.webp";

    assertThrows(
        ObjetoDeImagenNoEncontradoException.class,
        () ->
            confirmarImagenPrincipal.ejecutar(
                new ConfirmarImagenPrincipalComando(
                    producto.id(), objectKey, 100, 100, HASH, "a", "b")));
  }
}
