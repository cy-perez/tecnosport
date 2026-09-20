package co.tecnosport.api.application.catalogo;

import co.tecnosport.api.application.inventario.DisponibilidadDeVariantes;
import co.tecnosport.api.domain.catalogo.EstadoProducto;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.catalogo.Variante;
import java.util.Objects;

public final class VerFichaDeProducto {

  private final RepositorioProductos repositorioProductos;
  private final DisponibilidadDeVariantes disponibilidad;

  public VerFichaDeProducto(
      RepositorioProductos repositorioProductos, DisponibilidadDeVariantes disponibilidad) {
    this.repositorioProductos =
        Objects.requireNonNull(
            repositorioProductos, "El repositorio de productos no puede ser nulo.");
    this.disponibilidad =
        Objects.requireNonNull(disponibilidad, "La disponibilidad no puede ser nula.");
  }

  public FichaDeProducto ejecutar(VerFichaDeProductoComando comando) {
    Objects.requireNonNull(comando, "El comando no puede ser nulo.");
    Producto producto =
        repositorioProductos
            .buscarPorSlug(comando.slug())
            .filter(p -> p.estado() == EstadoProducto.PUBLICADO)
            .orElseThrow(() -> new ProductoNoEncontradoException(comando.slug()));
    return new FichaDeProducto(
        producto, disponibilidad.de(producto.variantes().stream().map(Variante::id).toList()));
  }
}
