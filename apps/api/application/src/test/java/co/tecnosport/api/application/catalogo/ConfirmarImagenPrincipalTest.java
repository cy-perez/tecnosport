package co.tecnosport.api.application.catalogo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        confirmarImagenPrincipal
            .ejecutar(
                new ConfirmarImagenPrincipalComando(
                    producto.id(), objectKey, 1000, 800, HASH, "alt es", "alt en"))
            .imagen();

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

  @Test
  void borraLaImagenAnteriorDelBucket() {
    Producto producto = productoDePrueba();
    repositorioProductos.conProductos(producto);
    String anterior = "productos/" + producto.id() + "/principal-vieja.webp";
    String nueva = "productos/" + producto.id() + "/principal-nueva.webp";
    almacenDeImagenes.conObjeto(anterior, 30_000);
    almacenDeImagenes.conObjeto(nueva, 45_000);

    var confirmacion =
        confirmarImagenPrincipal.ejecutar(
            new ConfirmarImagenPrincipalComando(
                producto.id(), nueva, 1000, 800, HASH, "alt es", "alt en"));

    // La anterior se va —si no, cada reemplazo deja pagando un objeto que ya nadie sirve— y la
    // recién subida se queda, aunque compartan prefijo.
    assertEquals(1, confirmacion.objetosAnterioresBorrados());
    assertFalse(confirmacion.limpiezaFallida());
    assertFalse(almacenDeImagenes.existe(anterior));
    assertTrue(almacenDeImagenes.existe(nueva));
  }

  @Test
  void noTocaLosObjetosDeOtroProducto() {
    Producto producto = productoDePrueba();
    repositorioProductos.conProductos(producto);
    String nueva = "productos/" + producto.id() + "/principal-nueva.webp";
    String ajena = "productos/" + UUID.randomUUID() + "/principal-vieja.webp";
    almacenDeImagenes.conObjeto(nueva, 45_000);
    almacenDeImagenes.conObjeto(ajena, 30_000);

    var confirmacion =
        confirmarImagenPrincipal.ejecutar(
            new ConfirmarImagenPrincipalComando(
                producto.id(), nueva, 1000, 800, HASH, "alt es", "alt en"));

    assertEquals(0, confirmacion.objetosAnterioresBorrados());
    assertTrue(almacenDeImagenes.existe(ajena));
  }

  @Test
  void noTocaElSetDeRotacionDelMismoProducto() {
    Producto producto = productoDePrueba();
    repositorioProductos.conProductos(producto);
    String nueva = "productos/" + producto.id() + "/principal-nueva.webp";
    String fotograma = "productos/" + producto.id() + "/rotacion/set-1/0.webp";
    almacenDeImagenes.conObjeto(nueva, 45_000);
    almacenDeImagenes.conObjeto(fotograma, 40_000);

    confirmarImagenPrincipal.ejecutar(
        new ConfirmarImagenPrincipalComando(
            producto.id(), nueva, 1000, 800, HASH, "alt es", "alt en"));

    // El prefijo llega hasta "principal-" a propósito: con solo el id del producto, reemplazar la
    // imagen principal se llevaría por delante los fotogramas del visor 360.
    assertTrue(almacenDeImagenes.existe(fotograma));
  }

  @Test
  void siLaLimpiezaFallaLaImagenIgualQuedaGuardada() {
    Producto producto = productoDePrueba();
    repositorioProductos.conProductos(producto);
    String nueva = "productos/" + producto.id() + "/principal-nueva.webp";
    almacenDeImagenes.conObjeto(nueva, 45_000);
    almacenDeImagenes.fallarAlEliminar = true;

    var confirmacion =
        confirmarImagenPrincipal.ejecutar(
            new ConfirmarImagenPrincipalComando(
                producto.id(), nueva, 1000, 800, HASH, "alt es", "alt en"));

    // Para cuando se limpia, la imagen ya está guardada: reportar un fallo seria mentir sobre una
    // operacion que funciono. Lo que queda es basura en el bucket, y se dice.
    assertTrue(confirmacion.limpiezaFallida());
    assertEquals(confirmacion.imagen(), repositorioProductos.ultimaImagenPrincipal);
  }
}
