package co.tecnosport.api.application.catalogo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.domain.catalogo.Categoria;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.catalogo.Marca;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.compartido.Slug;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SolicitarSubidaDeImagenPrincipalTest {

  private final RepositorioProductosFalso repositorioProductos = new RepositorioProductosFalso();
  private final AlmacenDeImagenesFalso almacenDeImagenes = new AlmacenDeImagenesFalso();
  private final SolicitarSubidaDeImagenPrincipal solicitarSubida =
      new SolicitarSubidaDeImagenPrincipal(repositorioProductos, almacenDeImagenes);

  private Producto productoDePrueba() {
    Marca marca = Marca.crear("TecnoSport");
    Categoria categoria = Categoria.crear("Bolsos", new Slug("bolsos"), LineaCatalogo.BOLSOS);
    return Producto.crear("Morral urbano", new Slug("morral-urbano"), "", marca, categoria);
  }

  @Test
  void generaUnaUrlFirmadaConUnaKeyDeObjetoDelProducto() {
    Producto producto = productoDePrueba();
    repositorioProductos.conProductos(producto);

    SolicitudDeSubida resultado =
        solicitarSubida.ejecutar(
            new SolicitarSubidaDeImagenPrincipalComando(producto.id(), "image/webp"));

    assertTrue(resultado.objectKey().startsWith("productos/" + producto.id() + "/principal-"));
    assertTrue(resultado.objectKey().endsWith(".webp"));
    assertEquals(resultado.objectKey(), almacenDeImagenes.ultimoObjectKeyFirmado);
    assertEquals("image/webp", almacenDeImagenes.ultimoContentTypeFirmado);
    assertTrue(resultado.url().contains(resultado.objectKey()));
  }

  /**
   * El formato que el cargador del catálogo usa desde el 21 de septiembre de 2026. Está aquí y no
   * solo en la lista blanca porque lo que importa no es que el tipo se acepte: es que la clave del
   * objeto termine en {@code .avif}, que es lo que hace que Cloud Storage lo sirva como AVIF y no
   * como un JPEG con la extensión equivocada.
   */
  @Test
  void avifSeAceptaYLaClaveTerminaEnAvif() {
    Producto producto = productoDePrueba();
    repositorioProductos.conProductos(producto);

    SolicitudDeSubida resultado =
        solicitarSubida.ejecutar(
            new SolicitarSubidaDeImagenPrincipalComando(producto.id(), "image/avif"));

    assertTrue(resultado.objectKey().endsWith(".avif"));
    assertEquals("image/avif", almacenDeImagenes.ultimoContentTypeFirmado);
  }

  @Test
  void productoInexistenteLanzaProductoNoEncontradoPorId() {
    UUID productoId = UUID.randomUUID();

    assertThrows(
        ProductoNoEncontradoPorIdException.class,
        () ->
            solicitarSubida.ejecutar(
                new SolicitarSubidaDeImagenPrincipalComando(productoId, "image/webp")));
  }

  @Test
  void contentTypeNoSoportadoLanzaIllegalArgument() {
    Producto producto = productoDePrueba();
    repositorioProductos.conProductos(producto);

    assertThrows(
        IllegalArgumentException.class,
        () ->
            solicitarSubida.ejecutar(
                new SolicitarSubidaDeImagenPrincipalComando(producto.id(), "application/pdf")));
  }
}
