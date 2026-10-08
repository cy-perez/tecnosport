package co.tecnosport.api.application.envio;

import co.tecnosport.api.domain.catalogo.Categoria;
import co.tecnosport.api.domain.envio.MedidasDeReferencia;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Lo que muestra la pantalla de referencias del panel ({@code adr/0071}): las medidas de la bolsa y
 * cada categoría que admite promedio, con su peso o sin él.
 *
 * <p>Las categorías sin peso van en la lista a propósito. Una categoría a la que nadie le puso peso
 * es un producto que solo se vende con recogida, y una pantalla que solo mostrara las que ya tienen
 * peso escondería justo las que hay que llenar.
 */
public record ReferenciasDeEnvio(
    Optional<MedidasDeReferencia> medidas, List<ReferenciasDeEnvio.CategoriaConPeso> categorias) {

  public ReferenciasDeEnvio {
    Objects.requireNonNull(medidas, "Las medidas no pueden ser nulas; vacías sí.");
    categorias = List.copyOf(categorias);
  }

  /**
   * Una hoja del árbol con su peso. {@code rama} es el nombre de la madre —"Dama" en "Ropa › Dama ›
   * Jeans"—, nulo si la hoja cuelga directo de la línea, como "Calzado › Unisex". Va aparte porque
   * "Jeans" existe en Dama y en Caballero, y sin la rama la pantalla mostraría dos filas iguales.
   *
   * <p>{@code pesoGramos} nulo es "sin promedio": esa categoría no se cotiza.
   */
  public record CategoriaConPeso(Categoria categoria, String rama, Integer pesoGramos) {

    public CategoriaConPeso {
      Objects.requireNonNull(categoria, "La categoría no puede ser nula.");
    }
  }
}
