package co.tecnosport.api.domain.proveedores;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * El título de una prenda vendida con su acompañante, a partir del de la prenda sola.
 *
 * <p>Decidido por el negocio el 12 de octubre de 2026: la falda con su blusa es un «Conjunto» —
 * «Conjunto falda y blusa», «Conjunto falda short y blusa»— y la camiseta o el polo con su gorra es
 * un «Set» —«Set camiseta oversize y gorra», «Set polo importado y gorra»—. La «básica» que escribe
 * La Riverah es la blusa del catálogo.
 *
 * <p>El género sale del título porque ya lo dice la categoría y, en medio, separaba la prenda de su
 * acompañante: «Set camiseta oversize para caballero y gorra». Lo demás se queda como lo dejaron
 * las reglas de la prenda —réplica, polo, marca—, que corren antes.
 */
public final class TituloDeSet {

  private static final Pattern GENERO =
      Pattern.compile(
          "\\s+(?:para|de)\\s+(?:dama|damas|caballero|caballeros|hombre|hombres|mujer|mujeres)"
              + "(?!\\p{L})",
          Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

  private static final Pattern ESPACIOS = Pattern.compile("\\s+");

  private TituloDeSet() {}

  /**
   * @param titulo el de la prenda sola, ya corregido
   * @return el del set; nulo si el de la prenda llegó nulo o si la prenda no arma set
   */
  public static String componer(String titulo, PrendaAcompanante acompanante) {
    if (titulo == null || titulo.isBlank() || acompanante == null) {
      return null;
    }
    String prenda = ESPACIOS.matcher(GENERO.matcher(titulo).replaceAll("")).replaceAll(" ").strip();
    if (prenda.isEmpty()) {
      return null;
    }
    // Solo la primera palabra baja: es el artículo. Una marca más adelante se queda como está.
    String enMedio = prenda.substring(0, 1).toLowerCase(Locale.ROOT) + prenda.substring(1);
    return switch (acompanante) {
      case BLUSA -> "Conjunto " + enMedio + " y blusa";
      case GORRA -> "Set " + enMedio + " y gorra";
      case OTRA -> null;
    };
  }
}
