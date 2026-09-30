package co.tecnosport.api.application.proveedores;

import co.tecnosport.api.domain.proveedores.ProductoExtraido;
import java.util.Objects;

/**
 * Lo que devolvió el extractor: el producto ya en tipos del dominio, el JSON tal cual llegó —que es
 * lo que el borrador guarda entero— y lo que costó.
 */
public record ResultadoExtraccion(
    ProductoExtraido producto, String jsonCrudo, UsoDelExtractor uso) {

  public ResultadoExtraccion {
    Objects.requireNonNull(producto, "El producto extraído no puede ser nulo.");
    Objects.requireNonNull(jsonCrudo, "El JSON crudo no puede ser nulo.");
    Objects.requireNonNull(uso, "El uso no puede ser nulo.");
  }
}
