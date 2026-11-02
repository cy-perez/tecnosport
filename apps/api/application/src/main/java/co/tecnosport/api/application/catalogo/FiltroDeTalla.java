package co.tecnosport.api.application.catalogo;

import co.tecnosport.api.domain.catalogo.Atributo;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import java.util.Objects;

/**
 * Una talla con existencia en una línea: «M» en ropa, «38» en calzado.
 *
 * <p>La línea va con la talla y no aparte porque los números se repiten entre las dos: un jean 38 y
 * un tenis 38 son tallas distintas, y filtrar «38» a secas mezclaría pantalones con zapatos. Por la
 * misma razón solo hay dos líneas posibles: bolsos y tecnología no tienen talla.
 *
 * <p>La talla queda normalizada como la compara la vitrina ({@link Atributo#tallaNormalizada}):
 * «m», «M» y «2xl» llegan a ser «M» y «XXL», que es como las devuelve {@link
 * ListarTallasDisponibles}.
 */
public record FiltroDeTalla(LineaCatalogo linea, String talla) {

  public FiltroDeTalla {
    Objects.requireNonNull(linea, "La línea de la talla no puede ser nula.");
    if (linea != LineaCatalogo.ROPA && linea != LineaCatalogo.CALZADO) {
      throw new IllegalArgumentException("Solo la ropa y el calzado tienen talla: " + linea);
    }
    if (talla == null || talla.isBlank()) {
      throw new IllegalArgumentException("La talla no puede estar vacía.");
    }
    talla = Atributo.tallaNormalizada(talla);
  }
}
