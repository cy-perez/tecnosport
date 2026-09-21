package co.tecnosport.api.application.catalogo;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.domain.catalogo.Categoria;
import co.tecnosport.api.domain.catalogo.GaleriaLlenaException;
import co.tecnosport.api.domain.catalogo.ImagenProducto;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.catalogo.Marca;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.catalogo.TipoImagen;
import co.tecnosport.api.domain.compartido.HashContenido;
import co.tecnosport.api.domain.compartido.Slug;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SolicitarSubidaDeImagenDeGaleriaTest {

  private final RepositorioProductosFalso repositorioProductos = new RepositorioProductosFalso();
  private final AlmacenDeImagenesFalso almacenDeImagenes = new AlmacenDeImagenesFalso();
  private final SolicitarSubidaDeImagenDeGaleria solicitar =
      new SolicitarSubidaDeImagenDeGaleria(repositorioProductos, almacenDeImagenes);

  private static Producto productoDePrueba() {
    Marca marca = Marca.crear("JBL");
    Categoria categoria =
        Categoria.crear("Parlantes", new Slug("parlantes"), LineaCatalogo.TECNOLOGIA);
    return Producto.crear("JBL Charge 6", new Slug("jbl-charge-6"), "", marca, categoria);
  }

  @Test
  void firmaUnObjetoBajoElPrefijoDeGaleriaDelProducto() {
    Producto producto = productoDePrueba();
    repositorioProductos.conProductos(producto);

    SolicitudDeSubida solicitud =
        solicitar.ejecutar(
            new SolicitarSubidaDeImagenDeGaleriaComando(producto.id(), "image/jpeg"));

    assertTrue(solicitud.objectKey().startsWith("productos/" + producto.id() + "/galeria-"));
    assertTrue(solicitud.objectKey().endsWith(".jpg"));
    assertTrue(solicitud.url().contains(solicitud.objectKey()));
  }

  @Test
  void elPrefijoNoEsElDeLaPrincipal() {
    Producto producto = productoDePrueba();
    repositorioProductos.conProductos(producto);

    SolicitudDeSubida solicitud =
        solicitar.ejecutar(
            new SolicitarSubidaDeImagenDeGaleriaComando(producto.id(), "image/webp"));

    // Importa de verdad: ConfirmarImagenPrincipal borra todo lo que cuelgue de 'principal-' cada
    // vez que se reemplaza la principal. Una imagen de galería ahí dentro desaparecería sola.
    assertTrue(!solicitud.objectKey().contains("/principal-"));
  }

  @Test
  void dosSolicitudesNoCompartenObjeto() {
    Producto producto = productoDePrueba();
    repositorioProductos.conProductos(producto);

    String primera =
        solicitar
            .ejecutar(new SolicitarSubidaDeImagenDeGaleriaComando(producto.id(), "image/jpeg"))
            .objectKey();
    String segunda =
        solicitar
            .ejecutar(new SolicitarSubidaDeImagenDeGaleriaComando(producto.id(), "image/jpeg"))
            .objectKey();

    assertTrue(!primera.equals(segunda));
  }

  @Test
  void conLaGaleriaLlenaNoFirmaNada() {
    Producto producto = productoDePrueba();
    for (int i = 0; i < Producto.TOPE_DE_GALERIA; i++) {
      producto.agregarImagenGaleria(imagenDeGaleria(i));
    }
    repositorioProductos.conProductos(producto);

    assertThrows(
        GaleriaLlenaException.class,
        () ->
            solicitar.ejecutar(
                new SolicitarSubidaDeImagenDeGaleriaComando(producto.id(), "image/jpeg")));
    // Un objeto que se sube y nunca se confirma no lo borra nadie: por eso el tope se pregunta
    // antes de firmar y no solo al agregar.
    assertNull(almacenDeImagenes.ultimoObjectKeyFirmado);
  }

  @Test
  void tipoDeContenidoNoSoportadoLanzaIllegalArgument() {
    Producto producto = productoDePrueba();
    repositorioProductos.conProductos(producto);

    assertThrows(
        IllegalArgumentException.class,
        () ->
            solicitar.ejecutar(
                new SolicitarSubidaDeImagenDeGaleriaComando(producto.id(), "image/gif")));
  }

  @Test
  void productoInexistenteLanzaProductoNoEncontradoPorId() {
    assertThrows(
        ProductoNoEncontradoPorIdException.class,
        () ->
            solicitar.ejecutar(
                new SolicitarSubidaDeImagenDeGaleriaComando(UUID.randomUUID(), "image/jpeg")));
  }

  private static ImagenProducto imagenDeGaleria(int orden) {
    return ImagenProducto.crear(
        TipoImagen.GALERIA,
        orden,
        "https://x/" + orden + ".jpg",
        "https://x/" + orden + ".jpg",
        2000,
        2000,
        120_000,
        new HashContenido("%064x".formatted(orden + 1)),
        "alt es",
        "alt en");
  }
}
