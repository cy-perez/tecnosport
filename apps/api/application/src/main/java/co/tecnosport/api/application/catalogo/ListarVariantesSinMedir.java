package co.tecnosport.api.application.catalogo;

import java.util.Comparator;
import java.util.Objects;

/**
 * El vigilante de lo que falta por medir.
 *
 * <p>Ordena <b>los productos publicados primero</b> y, dentro de cada grupo, por nombre: lo que
 * está a la venta es lo que hay que medir hoy, y un borrador puede esperar a que le pongan el
 * precio. El orden lo decide este caso de uso y no la consulta por el mismo criterio de {@code
 * ListarEnviosEnRevision} — la regla de negocio se lee en una clase que alguien prueba, no en un
 * {@code order by} que no prueba nadie.
 */
public final class ListarVariantesSinMedir {

  private final RepositorioProductos repositorioProductos;

  public ListarVariantesSinMedir(RepositorioProductos repositorioProductos) {
    this.repositorioProductos = Objects.requireNonNull(repositorioProductos);
  }

  public InventarioSinMedir ejecutar() {
    return new InventarioSinMedir(
        repositorioProductos.variantesSinMedir().stream()
            .sorted(
                Comparator.comparing(VarianteSinMedir::estadoProducto)
                    .reversed()
                    .thenComparing(VarianteSinMedir::nombreProducto)
                    .thenComparing(VarianteSinMedir::sku))
            .toList());
  }
}
