package co.tecnosport.api.domain.proveedores;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Las tallas que un proveedor de calzado maneja siempre, por género, como él las nombra, y cuántas
 * tallas más arriba las nombra en cada género (ADR-0089, ADR-0103).
 *
 * <p>Tenis Al Pie no escribe tallas en cada anuncio: las recuerda aparte —«Tallas hombre Eu 40 41
 * 42 43 44», «Talla dama Eu 36 37 38 39»— y valen para todo lo que manda. Son tallas EUR, y la EUR
 * no queda a la misma distancia de la nacional en los dos géneros: en dama es la nacional más una,
 * y en caballero, más tres —el EUR 42 de caballero mide 26,5 cm, que es la 39 colombiana; medido el
 * 3 de noviembre de 2026—. La tienda vende de la 35 a la 38 para dama y de la 37 a la 41 para
 * caballero.
 *
 * <p>Un tenis unisex lleva las dos listas, de menor a mayor, y solo tiene un ajuste si los dos
 * géneros lo comparten: si no, el mismo par se llamaría de dos maneras, y la ingesta lo parte en
 * dos borradores.
 *
 * @param dama las de dama como las nombra el proveedor, de menor a mayor
 * @param caballero las de caballero como las nombra el proveedor, de menor a mayor
 * @param ajusteDama cuántas tallas más arriba nombra las de dama
 * @param ajusteCaballero cuántas tallas más arriba nombra las de caballero
 */
public record TallasPorGenero(
    List<String> dama,
    List<String> caballero,
    AjusteDeTalla ajusteDama,
    AjusteDeTalla ajusteCaballero) {

  public TallasPorGenero {
    dama = numericas(dama);
    caballero = numericas(caballero);
    if (dama.isEmpty() && caballero.isEmpty()) {
      throw new ExcepcionDeDominio(
          "Las tallas del proveedor necesitan al menos las de dama o las de caballero.");
    }
    ajusteDama = Objects.requireNonNullElse(ajusteDama, AjusteDeTalla.NINGUNO);
    ajusteCaballero = Objects.requireNonNullElse(ajusteCaballero, AjusteDeTalla.NINGUNO);
  }

  /**
   * Las que el proveedor nombra para ese género. Unisex son las dos, sin repetir, de menor a mayor;
   * si el proveedor no maneja uno de los dos, las del otro.
   */
  public List<String> delProveedor(GeneroDeCalzado genero) {
    Objects.requireNonNull(genero, "El género del tenis no puede ser nulo.");
    return switch (genero) {
      case DAMA -> dama.isEmpty() ? caballero : dama;
      case CABALLERO -> caballero.isEmpty() ? dama : caballero;
      case UNISEX -> unidas();
    };
  }

  /**
   * El ajuste con que el proveedor nombra las tallas de ese género. Unisex solo tiene uno si los
   * dos géneros lo comparten, o si el proveedor maneja uno solo de ellos.
   *
   * @return vacío para un unisex de ajustes distintos: hay que partirlo ({@link #parteElUnisex})
   */
  public Optional<AjusteDeTalla> ajusteDe(GeneroDeCalzado genero) {
    Objects.requireNonNull(genero, "El género del tenis no puede ser nulo.");
    return switch (genero) {
      case DAMA -> Optional.of(dama.isEmpty() ? ajusteCaballero : ajusteDama);
      case CABALLERO -> Optional.of(caballero.isEmpty() ? ajusteDama : ajusteCaballero);
      case UNISEX -> parteElUnisex() ? Optional.empty() : Optional.of(unico());
    };
  }

  /**
   * ¿Un tenis unisex se vende en dos productos? Sí cuando el proveedor maneja los dos géneros y los
   * nombra con ajustes distintos: la EUR 40 sería la 39 de dama y la 37 de caballero.
   */
  public boolean parteElUnisex() {
    return !dama.isEmpty() && !caballero.isEmpty() && !ajusteDama.equals(ajusteCaballero);
  }

  /** Las que la tienda vende para ese género: las del proveedor corridas con su ajuste. */
  public List<String> queSeVenden(GeneroDeCalzado genero) {
    AjusteDeTalla ajuste =
        ajusteDe(genero)
            .orElseThrow(
                () ->
                    new ExcepcionDeDominio(
                        "Un tenis unisex de este proveedor no tiene una sola talla: se parte en"
                            + " dama y caballero."));
    List<String> vendidas = new ArrayList<>();
    for (String talla : delProveedor(genero)) {
      ajuste.tallaQueSeVende(talla, 1).ifPresent(vendidas::add);
    }
    return List.copyOf(vendidas);
  }

  private AjusteDeTalla unico() {
    if (dama.isEmpty()) {
      return ajusteCaballero;
    }
    return ajusteDama;
  }

  private List<String> unidas() {
    Set<String> todas = new LinkedHashSet<>(dama);
    todas.addAll(caballero);
    return todas.stream().sorted(Comparator.comparingInt(Integer::parseInt)).toList();
  }

  private static List<String> numericas(List<String> tallas) {
    if (tallas == null) {
      return List.of();
    }
    List<String> limpias = new ArrayList<>();
    for (String talla : tallas) {
      if (talla == null || talla.isBlank()) {
        continue;
      }
      String limpia = talla.strip();
      if (!limpia.matches("\\d{1,2}")) {
        throw new ExcepcionDeDominio(
            "Las tallas de calzado del proveedor son números: «" + limpia + "» no lo es.");
      }
      if (!limpias.contains(limpia)) {
        limpias.add(limpia);
      }
    }
    limpias.sort(Comparator.comparingInt(Integer::parseInt));
    return List.copyOf(limpias);
  }
}
