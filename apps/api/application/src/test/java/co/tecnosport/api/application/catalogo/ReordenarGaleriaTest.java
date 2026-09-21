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
import co.tecnosport.api.domain.compartido.HashContenido;
import co.tecnosport.api.domain.compartido.Slug;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReordenarGaleriaTest {

  private final RepositorioProductosFalso repositorioProductos = new RepositorioProductosFalso();
  private final ReordenarGaleria reordenarGaleria = new ReordenarGaleria(repositorioProductos);

  private static Producto productoDePrueba() {
    Marca marca = Marca.crear("JBL");
    Categoria categoria =
        Categoria.crear("Parlantes", new Slug("parlantes"), LineaCatalogo.TECNOLOGIA);
    return Producto.crear("JBL Charge 6", new Slug("jbl-charge-6"), "", marca, categoria);
  }

  private static ImagenProducto imagenEnLaGaleria(Producto producto, int orden) {
    ImagenProducto imagen =
        ImagenProducto.crear(
            TipoImagen.GALERIA,
            orden,
            "https://x/galeria-" + orden + ".jpg",
            "https://x/galeria-" + orden + ".webp",
            2000,
            2000,
            120_000,
            new HashContenido("%064x".formatted(orden + 1)),
            "alt es",
            "alt en");
    producto.agregarImagenGaleria(imagen);
    return imagen;
  }

  @Test
  void grabaLaGaleriaEnteraConElOrdenPedido() {
    Producto producto = productoDePrueba();
    ImagenProducto primera = imagenEnLaGaleria(producto, 0);
    ImagenProducto segunda = imagenEnLaGaleria(producto, 1);
    ImagenProducto tercera = imagenEnLaGaleria(producto, 2);
    repositorioProductos.conProductos(producto);

    List<ImagenProducto> resultado =
        reordenarGaleria.ejecutar(
            new ReordenarGaleriaComando(
                producto.id(), List.of(tercera.id(), primera.id(), segunda.id())));

    assertEquals(
        List.of(tercera.id(), primera.id(), segunda.id()),
        resultado.stream().map(ImagenProducto::id).toList());
    assertEquals(
        List.of(tercera.id(), primera.id(), segunda.id()),
        repositorioProductos.ordenGuardado.stream().map(ImagenProducto::id).toList());
    assertEquals(
        List.of(0, 1, 2),
        repositorioProductos.ordenGuardado.stream().map(ImagenProducto::orden).toList());
  }

  @Test
  void unProductoQueNoExisteFalla() {
    assertThrows(
        ProductoNoEncontradoPorIdException.class,
        () ->
            reordenarGaleria.ejecutar(
                new ReordenarGaleriaComando(UUID.randomUUID(), List.of(UUID.randomUUID()))));
  }

  /**
   * Lo que importa aquí no es la excepción —esa es del dominio y tiene su prueba—, sino que no se
   * grabe nada cuando el orden pedido no vale: media galería reordenada es peor que ninguna.
   */
  @Test
  void unOrdenQueNoEsLaGaleriaEnteraNoGrabaNada() {
    Producto producto = productoDePrueba();
    ImagenProducto primera = imagenEnLaGaleria(producto, 0);
    imagenEnLaGaleria(producto, 1);
    repositorioProductos.conProductos(producto);

    assertThrows(
        ImagenDeGaleriaNoEncontradaException.class,
        () ->
            reordenarGaleria.ejecutar(
                new ReordenarGaleriaComando(
                    producto.id(), List.of(primera.id(), UUID.randomUUID()))));

    assertNull(repositorioProductos.ordenGuardado);
  }

  /**
   * El comando se queda con una copia: quien lo arma no puede seguir cambiando la lista mientras el
   * caso de uso la recorre. Sin esto, el orden grabado no sería el que se pidió y no habría forma
   * de saberlo desde aquí.
   */
  @Test
  void elComandoNoSeDejaCambiarLaListaDespues() {
    Producto producto = productoDePrueba();
    ImagenProducto primera = imagenEnLaGaleria(producto, 0);
    ImagenProducto segunda = imagenEnLaGaleria(producto, 1);
    repositorioProductos.conProductos(producto);
    List<UUID> mutable = new java.util.ArrayList<>(List.of(primera.id(), segunda.id()));

    ReordenarGaleriaComando comando = new ReordenarGaleriaComando(producto.id(), mutable);
    mutable.clear();

    List<ImagenProducto> resultado = reordenarGaleria.ejecutar(comando);
    assertEquals(
        List.of(primera.id(), segunda.id()), resultado.stream().map(ImagenProducto::id).toList());
  }
}
