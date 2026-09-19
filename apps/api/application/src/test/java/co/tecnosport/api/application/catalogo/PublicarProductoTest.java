package co.tecnosport.api.application.catalogo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import co.tecnosport.api.domain.catalogo.Categoria;
import co.tecnosport.api.domain.catalogo.EstadoProducto;
import co.tecnosport.api.domain.catalogo.ImagenProducto;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.catalogo.Marca;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.catalogo.ProductoSinImagenPrincipalException;
import co.tecnosport.api.domain.catalogo.TipoImagen;
import co.tecnosport.api.domain.compartido.HashContenido;
import co.tecnosport.api.domain.compartido.Slug;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PublicarProductoTest {

  @Test
  void unBorradorConImagenPrincipalPasaAPublicado() {
    RepositorioProductosFalso repositorio = new RepositorioProductosFalso();
    Producto producto = productoDePrueba();
    producto.asignarImagenPrincipal(imagenPrincipal());
    repositorio.conProductos(producto);

    Producto publicado = new PublicarProducto(repositorio).ejecutar(producto.id());

    assertEquals(EstadoProducto.PUBLICADO, publicado.estado());
    assertEquals(
        EstadoProducto.PUBLICADO, repositorio.buscarPorId(producto.id()).orElseThrow().estado());
  }

  /**
   * La invariante que llevaba desde la Fase 1 en el dominio sin que nada pudiera dispararla: un
   * producto sin imagen principal saldría en la rejilla como un hueco gris.
   */
  @Test
  void unBorradorSinImagenPrincipalNoSePublica() {
    RepositorioProductosFalso repositorio = new RepositorioProductosFalso();
    Producto producto = productoDePrueba();
    repositorio.conProductos(producto);

    assertThrows(
        ProductoSinImagenPrincipalException.class,
        () -> new PublicarProducto(repositorio).ejecutar(producto.id()));

    assertEquals(
        EstadoProducto.BORRADOR, repositorio.buscarPorId(producto.id()).orElseThrow().estado());
  }

  /** Idempotente a propósito: el resultado de publicar lo ya publicado es el que se pedía. */
  @Test
  void publicarDosVecesNoEsUnError() {
    RepositorioProductosFalso repositorio = new RepositorioProductosFalso();
    Producto producto = productoDePrueba();
    producto.asignarImagenPrincipal(imagenPrincipal());
    repositorio.conProductos(producto);

    new PublicarProducto(repositorio).ejecutar(producto.id());
    Producto segunda = new PublicarProducto(repositorio).ejecutar(producto.id());

    assertEquals(EstadoProducto.PUBLICADO, segunda.estado());
  }

  @Test
  void unProductoQueNoExisteLanzaNoEncontrado() {
    assertThrows(
        ProductoNoEncontradoPorIdException.class,
        () -> new PublicarProducto(new RepositorioProductosFalso()).ejecutar(UUID.randomUUID()));
  }

  private static Producto productoDePrueba() {
    Marca marca = Marca.crear("JBL");
    Categoria categoria =
        Categoria.crear("Parlantes", new Slug("parlantes"), LineaCatalogo.TECNOLOGIA);
    return Producto.crear("JBL Go 5", new Slug("jbl-go-5"), "Parlante portátil", marca, categoria);
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
        new HashContenido("%064x".formatted(0)),
        "alt es",
        "alt en");
  }
}
