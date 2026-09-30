package co.tecnosport.api.application.proveedores;

import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.proveedores.AlertaBorrador;
import co.tecnosport.api.domain.proveedores.ProductoExtraido;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * La extracción ya contrastada: el producto, el JSON crudo, el precio con el que se sigue —el del
 * texto cuando lo hay; el del extractor solo cuando el texto no trae ninguno— y las alertas que una
 * persona tiene que mirar.
 */
public record ExtraccionEvaluada(
    ProductoExtraido producto,
    String jsonCrudo,
    Dinero precioProveedor,
    Set<AlertaBorrador> alertas,
    UsoDelExtractor uso) {

  public ExtraccionEvaluada {
    Objects.requireNonNull(producto);
    Objects.requireNonNull(jsonCrudo);
    alertas = Set.copyOf(alertas);
    Objects.requireNonNull(uso);
  }

  public Optional<Dinero> precioProveedorOpcional() {
    return Optional.ofNullable(precioProveedor);
  }
}
