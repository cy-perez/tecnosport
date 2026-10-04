package co.tecnosport.api.domain.proveedores;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Si el mensaje anuncia una réplica: los proveedores la marcan «1.1» —«NUEVA COLECCIÓN 1.1», «NUEVA
 * POLO 1.1🍯»— o «AAA» —«Superstar Importado AAA»—. AAA entró el 4 de octubre de 2026: el negocio
 * pidió titularla igual que la 1.1, «Tenis estilo Superstar».
 *
 * <p>Una réplica se publica con la marca Genérica y la marca original solo aparece en el título,
 * como «Camiseta estilo Puma - BMW» (decidido por el negocio el 3 de octubre de 2026). El extractor
 * lo detecta también; esto no depende de que lo haga. No confunde un precio ni una medida: «1.100»,
 * «11», «21.1» o «1.15» no son la marca.
 */
public final class PatronDeReplica {

  /**
   * «1.1» pegado, sin cifras alrededor y sin una unidad detrás: «1.1 kg», «1.1 L» o «1.1"» son
   * medidas. «1:1» se dejó fuera: es la relación de aspecto de un proyector o de una cámara.
   */
  private static final Pattern UNO_A_UNO =
      Pattern.compile(
          "(?<![\\d.,])1\\.1(?![\\d.,])(?!\\s*(?:\"|''|(?:kg|kgs|gr|g|lb|lbs|lt|l|ml|cm|mm|mts|m|"
              + "pulg|plg|oz|w|v|mah|ghz|tb|gb|x)\\b))",
          Pattern.CASE_INSENSITIVE);

  /**
   * «AAA» como palabra suelta, en mayúsculas o no. «AAAA» o «AAA123» no lo son: no es la marca sino
   * otra cosa pegada.
   */
  private static final Pattern TRIPLE_A =
      Pattern.compile("(?<![\\p{L}\\d])aaa(?![\\p{L}\\d])", Pattern.CASE_INSENSITIVE);

  /**
   * «Pilas AAA», «baterías AAA»: el tamaño de una pila, que un control o un juguete dice que usa.
   * Se mira lo que va justo antes, sin tildes.
   */
  private static final Pattern PILA_ANTES =
      Pattern.compile("(?:pilas?|baterias?|bateria)\\s*(?:tipo\\s*)?$");

  private static final Pattern DIACRITICOS = Pattern.compile("\\p{M}+");

  private PatronDeReplica() {}

  public static boolean esReplica(String texto) {
    return texto != null && (UNO_A_UNO.matcher(texto).find() || tieneTripleA(texto));
  }

  /**
   * El título sin la marca de réplica —«AAA» o «1.1»—, que nunca es parte del nombre del producto:
   * el extractor la quita y esto existe para que no dependa de que lo haga. Nulo si llegó nulo.
   */
  public static String sinMarca(String titulo) {
    if (titulo == null) {
      return null;
    }
    Matcher m = TRIPLE_A.matcher(titulo);
    StringBuilder sinTripleA = new StringBuilder();
    while (m.find()) {
      m.appendReplacement(sinTripleA, esDePila(titulo, m.start()) ? "$0" : " ");
    }
    m.appendTail(sinTripleA);
    String sinUnoAUno = UNO_A_UNO.matcher(sinTripleA).replaceAll(" ");
    return sinUnoAUno.replaceAll("\\s+", " ").strip();
  }

  private static boolean tieneTripleA(String texto) {
    Matcher m = TRIPLE_A.matcher(texto);
    while (m.find()) {
      if (!esDePila(texto, m.start())) {
        return true;
      }
    }
    return false;
  }

  private static boolean esDePila(String texto, int inicio) {
    String antes =
        DIACRITICOS
            .matcher(Normalizer.normalize(texto.substring(0, inicio), Normalizer.Form.NFD))
            .replaceAll("")
            .toLowerCase(Locale.ROOT)
            .stripTrailing();
    return PILA_ANTES.matcher(antes).find();
  }
}
