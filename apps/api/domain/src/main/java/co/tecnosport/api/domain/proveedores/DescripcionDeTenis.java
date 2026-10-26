package co.tecnosport.api.domain.proveedores;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * La descripción de un tenis (ADR-0092, decidido por el negocio el 26 de octubre de 2026): el
 * título, para quién es, las tallas que se venden y, al final, cómo está hecho.
 *
 * <ul>
 *   <li>«Tenis importados estilo New Balance 9060 para dama. Disponibles desde la talla 35 hasta la
 *       38.»
 *   <li>«Tenis importados estilo Alexander McQueen unisex. Disponibles desde la talla 35 hasta la
 *       43.»
 *   <li>«Tenis importados estilo Nike para caballero. Disponibles en la talla 42. Ultralivianos y
 *       con suela importada.»
 * </ul>
 *
 * <p>Las características las escribe el extractor con lo que dice el texto del proveedor —Imperio
 * Wicho sí lo dice; Tenis Al Pie no—. Lo que no se sabe no se escribe: sin género, sin «para»; sin
 * tallas numéricas, sin la frase de las tallas.
 */
public final class DescripcionDeTenis {

  private DescripcionDeTenis() {}

  /**
   * @param titulo el del tenis, como «Tenis importados estilo Nike Air Force 1»
   * @param genero para quién es; nulo si no se sabe
   * @param tallas las que la tienda vende, no las que nombra el proveedor
   * @param caracteristicas cómo está hecho; nulas o en blanco si el texto no lo dice
   * @return nula si no hay título: sin él no hay de qué hablar
   */
  public static String componer(
      String titulo, GeneroDeCalzado genero, List<String> tallas, String caracteristicas) {
    String apertura = apertura(titulo, genero, tallas);
    if (apertura == null) {
      return null;
    }
    String detalle = frase(caracteristicas);
    return detalle == null ? apertura : apertura + " " + detalle;
  }

  /**
   * Lo que sigue a la apertura en una descripción que se compuso así —«Ultralivianos y con suela
   * importada.»—, para recomponerla con otro título u otras tallas: cuando quien revisa corrige el
   * modelo, la descripción lo sigue (ADR-0092).
   *
   * @return vacío si la descripción no empieza como se compone con esos datos —quien revisa la
   *     reescribió, o es de antes—; una cadena vacía si no lleva características
   */
  public static Optional<String> caracteristicasDe(
      String descripcion, String titulo, GeneroDeCalzado genero, List<String> tallas) {
    String apertura = apertura(titulo, genero, tallas);
    if (descripcion == null || apertura == null) {
      return Optional.empty();
    }
    String escrita = descripcion.strip();
    if (escrita.equals(apertura)) {
      return Optional.of("");
    }
    if (escrita.startsWith(apertura + " ")) {
      return Optional.of(escrita.substring(apertura.length()).strip());
    }
    return Optional.empty();
  }

  private static String apertura(String titulo, GeneroDeCalzado genero, List<String> tallas) {
    if (titulo == null || titulo.isBlank()) {
      return null;
    }
    StringBuilder texto = new StringBuilder(sinPuntoFinal(titulo.strip()));
    if (genero != null) {
      texto.append(
          switch (genero) {
            case DAMA -> " para dama";
            case CABALLERO -> " para caballero";
            case UNISEX -> " unisex";
          });
    }
    texto.append('.');
    List<String> numericas = numericas(tallas);
    if (numericas.size() == 1) {
      texto.append(" Disponibles en la talla ").append(numericas.getFirst()).append('.');
    } else if (numericas.size() > 1) {
      texto
          .append(" Disponibles desde la talla ")
          .append(numericas.getFirst())
          .append(" hasta la ")
          .append(numericas.getLast())
          .append('.');
    }
    return texto.toString();
  }

  /** Las tallas numéricas, sin repetir y de menor a mayor; vacía si alguna no es un número. */
  private static List<String> numericas(List<String> tallas) {
    if (tallas == null) {
      return List.of();
    }
    List<String> limpias =
        tallas.stream().filter(Objects::nonNull).map(String::strip).distinct().toList();
    if (limpias.isEmpty() || !limpias.stream().allMatch(t -> t.matches("\\d{1,2}(?:[.,]5)?"))) {
      return List.of();
    }
    return limpias.stream()
        .sorted(Comparator.comparingDouble(t -> Double.parseDouble(t.replace(',', '.'))))
        .toList();
  }

  /** Con mayúscula inicial y punto final; nula si no hay nada. */
  private static String frase(String texto) {
    if (texto == null || texto.isBlank()) {
      return null;
    }
    String limpio = texto.strip();
    String conMayuscula = limpio.substring(0, 1).toUpperCase(Locale.ROOT) + limpio.substring(1);
    return conMayuscula.matches(".*[.!?]$") ? conMayuscula : conMayuscula + ".";
  }

  private static String sinPuntoFinal(String texto) {
    return texto.endsWith(".") ? texto.substring(0, texto.length() - 1).strip() : texto;
  }
}
