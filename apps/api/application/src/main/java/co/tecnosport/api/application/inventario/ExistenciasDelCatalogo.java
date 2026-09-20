package co.tecnosport.api.application.inventario;

import co.tecnosport.api.domain.catalogo.EstadoProducto;
import java.util.List;

/**
 * Las existencias de todo el catálogo activo, con los dos conteos que el panel enseña sin tener que
 * recorrer la lista.
 *
 * <p>Sin tope y sin paginar, mismo criterio que {@code InventarioSinMedir}: el número de
 * descuadradas es el producto de la consulta, y un conteo que se satura deja de moverse justo
 * cuando más hay que mirarlo.
 */
public record ExistenciasDelCatalogo(List<ExistenciaDeVariante> variantes) {

  public ExistenciasDelCatalogo {
    variantes = List.copyOf(variantes);
  }

  public int total() {
    return variantes.size();
  }

  /** Las que el catálogo cuenta distinto que el libro. Es el número que hay que vigilar. */
  public int totalDescuadradas() {
    return (int) variantes.stream().filter(ExistenciaDeVariante::descuadrada).count();
  }

  /** Descuadradas y además a la venta: las que le están mintiendo a alguien ahora mismo. */
  public int totalDescuadradasEnPublicados() {
    return (int)
        variantes.stream()
            .filter(ExistenciaDeVariante::descuadrada)
            .filter(variante -> variante.estadoProducto() == EstadoProducto.PUBLICADO)
            .count();
  }
}
