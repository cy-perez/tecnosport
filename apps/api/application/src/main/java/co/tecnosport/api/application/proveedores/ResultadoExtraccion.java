package co.tecnosport.api.application.proveedores;

import co.tecnosport.api.domain.proveedores.ProductoExtraido;
import java.util.List;
import java.util.Objects;

/**
 * Lo que devolvió el extractor: los productos ya en tipos del dominio, el JSON tal cual llegó —que
 * es lo que cada borrador guarda entero— y lo que costó.
 *
 * <p>Una lista porque un mensaje puede anunciar más de un producto —Violeta pone la chaqueta y el
 * jean del conjunto en el mismo pie de foto—, en el orden en que el mensaje los nombra. Vacía
 * cuando el mensaje no anuncia ninguno: un saludo, una promoción, un aviso de horario.
 */
public record ResultadoExtraccion(
    List<ProductoExtraido> productos, String jsonCrudo, UsoDelExtractor uso) {

  public ResultadoExtraccion {
    Objects.requireNonNull(productos, "La lista de productos no puede ser nula.");
    productos = List.copyOf(productos);
    Objects.requireNonNull(jsonCrudo, "El JSON crudo no puede ser nulo.");
    Objects.requireNonNull(uso, "El uso no puede ser nulo.");
  }

  /** Un mensaje, un producto: lo que devuelve el extractor sembrado. */
  public ResultadoExtraccion(ProductoExtraido producto, String jsonCrudo, UsoDelExtractor uso) {
    this(
        List.of(Objects.requireNonNull(producto, "El producto no puede ser nulo.")),
        jsonCrudo,
        uso);
  }
}
