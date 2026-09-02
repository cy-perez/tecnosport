package co.tecnosport.api.application.catalogo;

import co.tecnosport.api.application.compartido.ResultadoPaginado;
import co.tecnosport.api.domain.catalogo.Producto;
import java.util.Objects;

public final class BuscarProductos {

  private final RepositorioProductos repositorioProductos;

  public BuscarProductos(RepositorioProductos repositorioProductos) {
    this.repositorioProductos =
        Objects.requireNonNull(
            repositorioProductos, "El repositorio de productos no puede ser nulo.");
  }

  public ResultadoPaginado<Producto> ejecutar(BuscarProductosComando comando) {
    Objects.requireNonNull(comando, "El comando no puede ser nulo.");
    return repositorioProductos.buscar(
        comando.filtro(), comando.orden(), comando.cursor(), comando.tamanoPagina());
  }
}
