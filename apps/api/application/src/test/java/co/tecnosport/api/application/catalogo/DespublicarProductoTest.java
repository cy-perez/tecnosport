package co.tecnosport.api.application.catalogo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import co.tecnosport.api.domain.catalogo.Categoria;
import co.tecnosport.api.domain.catalogo.EstadoProducto;
import co.tecnosport.api.domain.catalogo.ImagenProducto;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.catalogo.Marca;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.catalogo.TipoImagen;
import co.tecnosport.api.domain.compartido.HashContenido;
import co.tecnosport.api.domain.compartido.Slug;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DespublicarProductoTest {

  private final RepositorioProductosFalso repositorio = new RepositorioProductosFalso();
  private final DespublicarProducto despublicar = new DespublicarProducto(repositorio);

  @Test
  void devuelveElProductoABorradorYLoGuarda() {
    Producto producto = productoPublicado();
    repositorio.conProductos(producto);

    Producto resultado = despublicar.ejecutar(producto.id());

    assertEquals(EstadoProducto.BORRADOR, resultado.estado());
    assertEquals(EstadoProducto.BORRADOR, repositorio.ultimoActualizado.estado());
  }

  /**
   * Retirar de la vitrina no es cancelar lo vendido: la imagen, las variantes y todo lo demás
   * siguen donde estaban, porque el producto vuelve a ser un borrador, no un producto a medias.
   */
  @Test
  void noPierdeNadaDelProducto() {
    Producto producto = productoPublicado();
    repositorio.conProductos(producto);

    Producto resultado = despublicar.ejecutar(producto.id());

    assertEquals(producto.nombre(), resultado.nombre());
    assertEquals(producto.slug(), resultado.slug());
    assertEquals(1, resultado.imagenPrincipal().stream().count());
  }

  /** Idempotente, igual que publicar: despublicar un borrador deja un borrador. */
  @Test
  void despublicarUnBorradorNoEsUnError() {
    Producto borrador = productoDePrueba();
    repositorio.conProductos(borrador);

    assertEquals(EstadoProducto.BORRADOR, despublicar.ejecutar(borrador.id()).estado());
  }

  @Test
  void unIdQueNoExisteLoDice() {
    assertThrows(
        ProductoNoEncontradoPorIdException.class, () -> despublicar.ejecutar(UUID.randomUUID()));
  }

  private static Producto productoPublicado() {
    Producto producto = productoDePrueba();
    producto.asignarImagenPrincipal(imagenPrincipal());
    producto.publicar();
    return producto;
  }

  private static Producto productoDePrueba() {
    Marca marca = Marca.crear("JBL");
    Categoria categoria =
        Categoria.crear("Parlantes", new Slug("parlantes"), LineaCatalogo.TECNOLOGIA);
    return Producto.crear("JBL Charge 6", new Slug("jbl-charge-6"), "", marca, categoria);
  }

  private static ImagenProducto imagenPrincipal() {
    return ImagenProducto.crear(
        TipoImagen.PRINCIPAL,
        0,
        "https://x/0.jpg",
        "https://x/0.webp",
        2000,
        2000,
        1000,
        new HashContenido("%064x".formatted(0)),
        "alt es",
        "alt en");
  }
}
