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
 * <p>Desde el 29 de octubre de 2026 (ADR-0101), si el proveedor nombra el combo con su palabra
 * —«Dúo $69.900», «Set falda + básica»—, el título la conserva: «Dúo pantaloneta con licra y
 * gorra». Y con el orden en que nombra las piezas: «Set básica + falda» es «Set blusa y falda
 * short». Sin palabra, las de arriba.
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
    return componer(titulo, acompanante, null);
  }

  /**
   * @param palabra la del proveedor para su combo —«Dúo», «Set», «Conjunto»—; nula si no la dice
   */
  public static String componer(String titulo, PrendaAcompanante acompanante, String palabra) {
    return componer(titulo, acompanante, palabra, false);
  }

  /**
   * @param acompananteDelante si el proveedor nombra primero el acompañante: «Set básica + falda»
   */
  public static String componer(
      String titulo, PrendaAcompanante acompanante, String palabra, boolean acompananteDelante) {
    if (titulo == null || titulo.isBlank() || acompanante == null) {
      return null;
    }
    String prenda = ESPACIOS.matcher(GENERO.matcher(titulo).replaceAll("")).replaceAll(" ").strip();
    if (prenda.isEmpty()) {
      return null;
    }
    // Solo la primera palabra baja: es el artículo. Una marca más adelante se queda como está.
    String enMedio = prenda.substring(0, 1).toLowerCase(Locale.ROOT) + prenda.substring(1);
    String delAcompanante =
        switch (acompanante) {
          case BLUSA -> "blusa";
          case GORRA -> "gorra";
          case OTRA -> null;
        };
    if (delAcompanante == null) {
      return null;
    }
    String inicio =
        palabra != null ? palabra : acompanante == PrendaAcompanante.BLUSA ? "Conjunto" : "Set";
    return acompananteDelante
        ? inicio + " " + delAcompanante + " y " + enMedio
        : inicio + " " + enMedio + " y " + delAcompanante;
  }
}
