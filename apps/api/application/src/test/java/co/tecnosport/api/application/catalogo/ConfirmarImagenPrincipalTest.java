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
                producto.id(), objectKey, 1000, 800, "alt es", "alt en"));

    assertEquals(TipoImagen.PRINCIPAL, imagen.tipo());
    assertEquals(1000, imagen.ancho());
    assertEquals(800, imagen.alto());
    assertEquals(45_000, imagen.bytes());
    assertEquals(objectKey, imagen.hash());
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
                new ConfirmarImagenPrincipalComando(productoId, objectKey, 100, 100, "a", "b")));
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
                    producto.id(), objectKeyDeOtroProducto, 100, 100, "a", "b")));
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
                new ConfirmarImagenPrincipalComando(producto.id(), objectKey, 100, 100, "a", "b")));
  }
}
