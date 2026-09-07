package co.tecnosport.api.application.catalogo;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import co.tecnosport.api.domain.catalogo.Categoria;
import co.tecnosport.api.domain.catalogo.ImagenProducto;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.catalogo.Marca;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.catalogo.TipoImagen;
import co.tecnosport.api.domain.compartido.HashContenido;
import co.tecnosport.api.domain.compartido.Slug;
import org.junit.jupiter.api.Test;

class VerFichaDeProductoTest {

  @Test
  void devuelveElProductoPublicado() {
    RepositorioProductosFalso repositorio = new RepositorioProductosFalso();
    Producto producto = productoPublicado();
    repositorio.conProductos(producto);

    Producto encontrado =
        new VerFichaDeProducto(repositorio)
            .ejecutar(new VerFichaDeProductoComando(producto.slug()));

    assertSame(producto, encontrado);
  }

  @Test
  void unProductoEnBorradorNoSeVePorFuera() {
    RepositorioProductosFalso repositorio = new RepositorioProductosFalso();
    Producto borrador = productoDePrueba();
    repositorio.conProductos(borrador);

    assertThrows(
        ProductoNoEncontradoException.class,
        () ->
            new VerFichaDeProducto(repositorio)
                .ejecutar(new VerFichaDeProductoComando(borrador.slug())));
  }

  @Test
  void unSlugInexistenteLanzaLaMismaExcepcionQueUnBorrador() {
    RepositorioProductosFalso repositorio = new RepositorioProductosFalso();

    assertThrows(
        ProductoNoEncontradoException.class,
        () ->
            new VerFichaDeProducto(repositorio)
                .ejecutar(new VerFichaDeProductoComando(new Slug("no-existe"))));
  }

  private static Producto productoPublicado() {
    Producto producto = productoDePrueba();
    producto.asignarImagenPrincipal(imagenPrincipal());
    producto.publicar();
    return producto;
  }

  private static Producto productoDePrueba() {
    Marca marca = Marca.crear("TecnoSport");
    Categoria categoria =
        Categoria.crear("Ropa deportiva", new Slug("ropa-deportiva"), LineaCatalogo.ROPA_Y_CALZADO);
    return Producto.crear(
        "Camiseta running Dry-Fit", new Slug("camiseta-running-dry-fit"), "", marca, categoria);
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
