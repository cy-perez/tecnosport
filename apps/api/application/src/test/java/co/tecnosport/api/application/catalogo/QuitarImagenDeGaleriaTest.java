package co.tecnosport.api.application.catalogo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

class QuitarImagenDeGaleriaTest {

  private final RepositorioProductosFalso repositorioProductos = new RepositorioProductosFalso();
  private final AlmacenDeImagenesFalso almacenDeImagenes = new AlmacenDeImagenesFalso();
  private final QuitarImagenDeGaleria quitarImagenDeGaleria =
      new QuitarImagenDeGaleria(repositorioProductos, almacenDeImagenes);

  private static Producto productoDePrueba() {
    Marca marca = Marca.crear("JBL");
    Categoria categoria =
        Categoria.crear("Parlantes", new Slug("parlantes"), LineaCatalogo.TECNOLOGIA);
    return Producto.crear("JBL Charge 6", new Slug("jbl-charge-6"), "", marca, categoria);
  }

  /** Una imagen ya guardada, con la URL que el almacén le habría dado a ese objeto. */
  private ImagenProducto imagenEnLaGaleria(Producto producto, String sufijo, int orden) {
    String objectKey = "productos/" + producto.id() + "/galeria-" + sufijo + ".jpg";
    almacenDeImagenes.conObjeto(objectKey, 120_000);
    String url = almacenDeImagenes.urlPublica(objectKey);
    ImagenProducto imagen =
        ImagenProducto.crear(
            TipoImagen.GALERIA,
            orden,
            url,
            url,
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
  void quitaLaFilaYBorraElObjetoDelBucket() {
    Producto producto = productoDePrueba();
    ImagenProducto imagen = imagenEnLaGaleria(producto, "uno", 0);
    repositorioProductos.conProductos(producto);

    ImagenDeGaleriaQuitada resultado =
        quitarImagenDeGaleria.ejecutar(
            new QuitarImagenDeGaleriaComando(producto.id(), imagen.id()));

    assertTrue(resultado.objetoBorrado());
    assertFalse(resultado.limpiezaFallida());
    assertEquals(List.of(imagen.id()), repositorioProductos.imagenesDeGaleriaEliminadas);
    assertFalse(almacenDeImagenes.existe("productos/" + producto.id() + "/galeria-uno.jpg"));
    assertTrue(producto.galeria().isEmpty());
  }

  @Test
  void noSeLlevaLasImagenesHermanas() {
    Producto producto = productoDePrueba();
    ImagenProducto primera = imagenEnLaGaleria(producto, "uno", 0);
    imagenEnLaGaleria(producto, "dos", 1);
    imagenEnLaGaleria(producto, "tres", 2);
    repositorioProductos.conProductos(producto);

    quitarImagenDeGaleria.ejecutar(new QuitarImagenDeGaleriaComando(producto.id(), primera.id()));

    // Las tres comparten el prefijo 'galeria-'. Borrar por prefijo, que es lo que hace la
    // principal,
    // habría dejado la ficha sin ninguna.
    assertEquals(2, producto.galeria().size());
    assertTrue(almacenDeImagenes.existe("productos/" + producto.id() + "/galeria-dos.jpg"));
    assertTrue(almacenDeImagenes.existe("productos/" + producto.id() + "/galeria-tres.jpg"));
    assertTrue(almacenDeImagenes.prefijosEliminados.isEmpty());
  }

  @Test
  void unaImagenQueNoEsDeEsteProductoNoSeQuita() {
    Producto producto = productoDePrueba();
    imagenEnLaGaleria(producto, "uno", 0);
    repositorioProductos.conProductos(producto);

    assertThrows(
        ImagenDeGaleriaNoEncontradaException.class,
        () ->
            quitarImagenDeGaleria.ejecutar(
                new QuitarImagenDeGaleriaComando(producto.id(), UUID.randomUUID())));
    assertTrue(repositorioProductos.imagenesDeGaleriaEliminadas.isEmpty());
  }

  @Test
  void unaImagenDeFueraDelAlmacenSaleDeLaFichaSinIntentarBorrarNada() {
    Producto producto = productoDePrueba();
    // El catálogo sembrado de local y dev trae imágenes de picsum.photos.
    ImagenProducto dePicsum =
        ImagenProducto.crear(
            TipoImagen.GALERIA,
            0,
            "https://picsum.photos/seed/jbl-charge-6-galeria-1/800/600",
            "https://picsum.photos/seed/jbl-charge-6-galeria-1/800/600",
            800,
            600,
            50_000,
            new HashContenido("%064x".formatted(5)),
            "alt es",
            "alt en");
    producto.agregarImagenGaleria(dePicsum);
    repositorioProductos.conProductos(producto);

    ImagenDeGaleriaQuitada resultado =
        quitarImagenDeGaleria.ejecutar(
            new QuitarImagenDeGaleriaComando(producto.id(), dePicsum.id()));

    assertFalse(resultado.objetoBorrado());
    assertFalse(resultado.limpiezaFallida());
    assertTrue(almacenDeImagenes.objetosEliminados.isEmpty());
    assertEquals(List.of(dePicsum.id()), repositorioProductos.imagenesDeGaleriaEliminadas);
  }

  @Test
  void siElBucketFallaAlBorrarLaImagenIgualSaleDeLaFicha() {
    Producto producto = productoDePrueba();
    ImagenProducto imagen = imagenEnLaGaleria(producto, "uno", 0);
    repositorioProductos.conProductos(producto);
    almacenDeImagenes.fallarAlEliminar = true;

    ImagenDeGaleriaQuitada resultado =
        quitarImagenDeGaleria.ejecutar(
            new QuitarImagenDeGaleriaComando(producto.id(), imagen.id()));

    // La fila ya se fue: reportar esto como fallo sería mentir sobre lo que pasó. Lo que queda es
    // un objeto sin reclamar, y eso se dice.
    assertTrue(resultado.limpiezaFallida());
    assertFalse(resultado.objetoBorrado());
    assertEquals(List.of(imagen.id()), repositorioProductos.imagenesDeGaleriaEliminadas);
    assertTrue(producto.galeria().isEmpty());
  }

  @Test
  void productoInexistenteLanzaProductoNoEncontradoPorId() {
    assertThrows(
        ProductoNoEncontradoPorIdException.class,
        () ->
            quitarImagenDeGaleria.ejecutar(
                new QuitarImagenDeGaleriaComando(UUID.randomUUID(), UUID.randomUUID())));
  }
}
