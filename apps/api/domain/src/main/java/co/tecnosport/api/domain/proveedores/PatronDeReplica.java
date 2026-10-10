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
 *
 * <p>Cuando el mensaje marca la réplica pero no nombra marca ni modelo —«Pantalón Jogger para dama
 * ✨Calidad 1.1»—, no hay «estilo» que escribir: el título dice «importado», «Pantalón jogger para
 * dama importado» (decidido por el negocio el 9 de octubre de 2026). Antes el título quedaba
 * colgando, «Pantalón jogger para dama estilo» ({@link #tituloDeReplica}).
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

  /** «estilo» al final, sin la marca que debía seguirle. */
  private static final Pattern ESTILO_COLGANDO =
      Pattern.compile("\\s*\\bestilo\\W*$", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

  private static final Pattern ESTILO =
      Pattern.compile("(?<!\\p{L})estilo(?!\\p{L})", Pattern.CASE_INSENSITIVE);

  /** La palabra «réplica», con o sin tilde, en singular o plural. */
  private static final Pattern PALABRA_REPLICA =
      Pattern.compile(
          "(?<!\\p{L})r[eé]plicas?(?!\\p{L})", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

  private static final Pattern IMPORTADO =
      Pattern.compile("(?<!\\p{L})importad[oa]s?(?!\\p{L})", Pattern.CASE_INSENSITIVE);

  private PatronDeReplica() {}

  /**
   * Si el texto dice «réplica» con todas sus letras. Es lo único, además de «1.1» y «AAA», con que
   * se le cree al extractor que un producto es réplica: Imperio Wicho escribe «Importado» en casi
   * todo, y el 9 de octubre de 2026 el modelo empezó a marcar esos anuncios como réplica y a
   * titularlos «Tenis estilo Cab importado». «Importado» no es una marca de réplica.
   */
  public static boolean diceReplica(String texto) {
    return texto != null && PALABRA_REPLICA.matcher(texto).find();
  }

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

  /**
   * El título de una réplica: si nombra la marca —«Tenis estilo Superstar»— queda igual; si no,
   * termina en «importado», y un «estilo» que quedó colgando al final se cambia por esa palabra.
   * Nulo si llegó nulo.
   */
  public static String tituloDeReplica(String titulo) {
    if (titulo == null) {
      return null;
    }
    String sinColgar = ESTILO_COLGANDO.matcher(titulo).replaceFirst("").strip();
    if (sinColgar.isEmpty()) {
      return titulo;
    }
    if (ESTILO.matcher(sinColgar).find() || IMPORTADO.matcher(sinColgar).find()) {
      return sinColgar;
    }
    return sinColgar + " importado";
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
