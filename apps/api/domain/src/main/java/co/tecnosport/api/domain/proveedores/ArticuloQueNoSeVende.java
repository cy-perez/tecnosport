package co.tecnosport.api.domain.proveedores;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lo que la tienda no vende, de ningún proveedor (ADR-0101, 29 de octubre de 2026): la pijama y la
 * correa. Se mira el título que dejó la extracción, no el texto entero del anuncio: «correa»
 * también es la tira de un bolso o de una sandalia, y solo el artículo que se llama a sí mismo
 * correa es una correa.
 *
 * <ul>
 *   <li>La pijama, la nombre como la nombre: «Pijama short», «Conjunto pijama», «Set de pijamas».
 *   <li>La correa o el cinturón, solo si el título empieza por ahí: «Correa importada» sí; «Bolso
 *       con correa ancha», no.
 * </ul>
 */
public final class ArticuloQueNoSeVende {

  private static final Pattern PIJAMA =
      Pattern.compile(
          "(?<!\\p{L})(pijamas?|piyamas?)(?!\\p{L})",
          Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

  private static final Pattern CORREA =
      Pattern.compile(
          "^\\s*(correas?|cinturón|cinturon|cinturones)(?!\\p{L})",
          Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

  private ArticuloQueNoSeVende() {}

  /**
   * @param titulo el que dejó la extracción; puede ser nulo
   * @return por qué se descarta, con el artículo en minúsculas («pijama», «correa»); vacío si se
   *     vende
   */
  public static Optional<CierreDePublicacion> porElTitulo(String titulo) {
    if (titulo == null) {
      return Optional.empty();
    }
    if (PIJAMA.matcher(titulo).find()) {
      return Optional.of(
          new CierreDePublicacion(MotivoDePublicacion.ARTICULO_QUE_NO_SE_VENDE, "pijama"));
    }
    Matcher correa = CORREA.matcher(titulo);
    if (correa.find()) {
      String articulo = correa.group(1).toLowerCase(Locale.ROOT);
      return Optional.of(
          new CierreDePublicacion(
              MotivoDePublicacion.ARTICULO_QUE_NO_SE_VENDE,
              articulo.startsWith("cintur") ? "cinturón" : "correa"));
    }
    return Optional.empty();
  }
}
