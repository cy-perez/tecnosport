package co.tecnosport.api.domain.proveedores;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * El título de un polo, y cómo saber que un anuncio vende polos.
 *
 * <p>Decidido por el negocio el 11 de octubre de 2026, con las 23 camisas tipo polo de La Riverah,
 * que salieron como «Camiseta estilo Polo importada»: en una réplica, «estilo Polo» quiere decir la
 * marca Polo Ralph Lauren, no la prenda. El título de un polo es <b>«Polo» y el resto</b>, sin
 * «Camiseta» ni «Camisa»: «Polo importado» si es réplica o importado, con «estilo» y la marca solo
 * si nombra una —«Polo importado estilo Puma»—. La prenda va en el tipo ({@link
 * TipoProductoProveedor#POLO}) y en la categoría, Caballero › Polos.
 *
 * <p>El prompt pide lo mismo; esto existe para que no dependa de que el modelo lo recuerde.
 */
public final class TituloDePolo {

  /**
   * Lo que en el texto de un mayorista anuncia un polo y no una camiseta: «tipo polo», «camiseta
   * polo», «cuello polo», «NUEVA POLO 1.1». «Polo» solo, no: puede ser la marca.
   */
  private static final Pattern ANUNCIA_POLO =
      Pattern.compile(
          "(?<!\\p{L})(?:tipo|cuello|camisetas?|camisas?|nuevas?)\\s+polos?(?!\\p{L})"
              + "|(?<!\\p{L})polos?\\s+(?:1\\.1|aaa?|para)(?!\\p{L})",
          Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

  /** La prenda al comienzo del título: «Camiseta estilo Polo», «Camisa tipo polo», «Polo». */
  private static final Pattern PRENDA_AL_COMIENZO =
      Pattern.compile(
          "^(?:(?:camisetas?|camisas?)(?:\\s+(?:tipo|estilo))?\\s+polos?|camisetas?|camisas?|polos?)"
              + "(?!\\p{L})",
          Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

  private static final Pattern IMPORTADO =
      Pattern.compile("(?<!\\p{L})importad[oa]s?(?!\\p{L})", Pattern.CASE_INSENSITIVE);

  private static final Pattern ESTILO_Y_MARCA =
      Pattern.compile("(?<!\\p{L})estilo\\s+(.+)$", Pattern.CASE_INSENSITIVE);

  private static final Pattern ESPACIOS = Pattern.compile("\\s+");

  private TituloDePolo() {}

  /** Si el texto del anuncio vende polos, aunque el extractor haya dicho camiseta. */
  public static boolean anunciaPolo(String texto) {
    return texto != null && ANUNCIA_POLO.matcher(texto).find();
  }

  /**
   * @param importado si es réplica, o el título ya decía importado
   * @return el título con la forma de un polo; nulo si llegó nulo
   */
  public static String componer(String titulo, boolean importado) {
    if (titulo == null) {
      return null;
    }
    String resto = PRENDA_AL_COMIENZO.matcher(titulo.strip()).replaceFirst("");
    boolean diceImportado = importado || IMPORTADO.matcher(resto).find();
    resto = IMPORTADO.matcher(resto).replaceAll(" ");
    String marca = null;
    Matcher estilo = ESTILO_Y_MARCA.matcher(resto);
    if (estilo.find()) {
      marca = ESPACIOS.matcher(estilo.group(1)).replaceAll(" ").strip();
      resto = resto.substring(0, estilo.start());
      // «estilo Polo» en un polo es la prenda, no la marca.
      if (marca.isEmpty() || marca.toLowerCase(Locale.ROOT).matches("polos?")) {
        marca = null;
      }
    }
    resto = ESPACIOS.matcher(resto).replaceAll(" ").strip();
    StringBuilder compuesto = new StringBuilder("Polo");
    if (diceImportado) {
      compuesto.append(" importado");
    }
    if (!resto.isEmpty()) {
      compuesto.append(' ').append(resto);
    }
    if (marca != null) {
      compuesto.append(" estilo ").append(marca);
    }
    return compuesto.toString();
  }
}
