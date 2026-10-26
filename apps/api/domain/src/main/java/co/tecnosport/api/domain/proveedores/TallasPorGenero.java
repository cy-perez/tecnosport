package co.tecnosport.api.domain.proveedores;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Las tallas que un proveedor de calzado maneja siempre, por género, como él las nombra, y cuántas
 * tallas más arriba las nombra (ADR-0089).
 *
 * <p>Tenis Al Pie no escribe tallas en cada anuncio: las recuerda aparte —«Tallas hombre Eu 40 41
 * 42 43 44», «Talla dama Eu 36 37 38 39»— y valen para todo lo que manda. Son tallas EUR, que es la
 * nacional más una (ADR-0085): el ajuste es 1, y la tienda vende de la 39 a la 43 para caballero y
 * de la 35 a la 38 para dama. Un tenis unisex lleva las dos, de la 35 a la 43.
 *
 * @param dama las de dama como las nombra el proveedor, de menor a mayor
 * @param caballero las de caballero como las nombra el proveedor, de menor a mayor
 * @param ajuste cuántas tallas más arriba las nombra el proveedor
 */
public record TallasPorGenero(List<String> dama, List<String> caballero, AjusteDeTalla ajuste) {

  public TallasPorGenero {
    dama = numericas(dama);
    caballero = numericas(caballero);
    if (dama.isEmpty() && caballero.isEmpty()) {
      throw new ExcepcionDeDominio(
          "Las tallas del proveedor necesitan al menos las de dama o las de caballero.");
    }
    ajuste = Objects.requireNonNullElse(ajuste, AjusteDeTalla.NINGUNO);
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

  /** Las que la tienda vende para ese género: las del proveedor corridas con el ajuste. */
  public List<String> queSeVenden(GeneroDeCalzado genero) {
    List<String> vendidas = new ArrayList<>();
    for (String talla : delProveedor(genero)) {
      ajuste.tallaQueSeVende(talla, 1).ifPresent(vendidas::add);
    }
    return List.copyOf(vendidas);
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
