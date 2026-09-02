package co.tecnosport.api.application.catalogo;

import co.tecnosport.api.domain.catalogo.EstadoProducto;
import co.tecnosport.api.domain.catalogo.Producto;
import java.util.Objects;

public final class VerFichaDeProducto {

  private final RepositorioProductos repositorioProductos;

  public VerFichaDeProducto(RepositorioProductos repositorioProductos) {
    this.repositorioProductos =
        Objects.requireNonNull(
            repositorioProductos, "El repositorio de productos no puede ser nulo.");
  }

  public Producto ejecutar(VerFichaDeProductoComando comando) {
    Objects.requireNonNull(comando, "El comando no puede ser nulo.");
    return repositorioProductos
        .buscarPorSlug(comando.slug())
        .filter(producto -> producto.estado() == EstadoProducto.PUBLICADO)
        .orElseThrow(() -> new ProductoNoEncontradoException(comando.slug()));
  }
}
