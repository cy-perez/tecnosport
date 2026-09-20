package co.tecnosport.api.application.catalogo;

import co.tecnosport.api.application.compartido.ResultadoPaginado;
import co.tecnosport.api.application.inventario.DisponibilidadDeVariantes;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.catalogo.Variante;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * La rejilla de la vitrina. Dos lecturas, no una: los productos de la página y, con los ids de sus
 * variantes, el libro de inventario que dice cuáles se pueden comprar (adr/0050).
 *
 * <p>La segunda lectura está acotada a la página, que es lo que la hace pagable: no es el catálogo
 * entero, son las variantes que se van a pintar.
 */
public final class BuscarProductos {

  private final RepositorioProductos repositorioProductos;
  private final DisponibilidadDeVariantes disponibilidad;

  public BuscarProductos(
      RepositorioProductos repositorioProductos, DisponibilidadDeVariantes disponibilidad) {
    this.repositorioProductos =
        Objects.requireNonNull(
            repositorioProductos, "El repositorio de productos no puede ser nulo.");
    this.disponibilidad =
        Objects.requireNonNull(disponibilidad, "La disponibilidad no puede ser nula.");
  }

  public CatalogoPaginado ejecutar(BuscarProductosComando comando) {
    Objects.requireNonNull(comando, "El comando no puede ser nulo.");
    ResultadoPaginado<Producto> pagina =
        repositorioProductos.buscar(
            comando.filtro(), comando.orden(), comando.cursor(), comando.tamanoPagina());
    List<UUID> variantes =
        pagina.items().stream()
            .flatMap(producto -> producto.variantes().stream())
            .map(Variante::id)
            .toList();
    return new CatalogoPaginado(pagina, disponibilidad.de(variantes));
  }
}
