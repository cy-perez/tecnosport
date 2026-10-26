package co.tecnosport.api.domain.proveedores;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * El título de un tenis: <b>«Tenis importados estilo &lt;Marca&gt; &lt;Modelo&gt;»</b>, como «Tenis
 * importados estilo Nike Air Force 1». Decidido por el negocio el 25 de octubre de 2026 para todo
 * el calzado (ADR-0089): todo el que se vende es réplica importada.
 *
 * <ul>
 *   <li><b>Sin el género</b>: dama, caballero o unisex quedan solo en la categoría. Un título «para
 *       dama» de un tenis que se publica en Unisex diría una cosa y la categoría otra.
 *   <li><b>Sin modelo</b> si no se sabe con seguridad: «Tenis importados estilo Nike».
 *   <li><b>Sin marca</b> si no se lee ninguna: «Tenis importados».
 * </ul>
 *
 * <p>El prompt pide lo mismo; esto existe para que no dependa de que el modelo lo recuerde.
 */
public final class TituloDeTenis {

  private static final String BASE = "Tenis importados";

  /** Lo que sigue a «estilo», hasta el final. */
  private static final Pattern ESTILO_Y_RESTO =
      Pattern.compile(
          "(?<!\\p{L})estilo\\s+(.+)$", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

  /** Donde termina el nombre del tenis: el género, «importado» o la talla. */
  private static final Pattern FIN_DEL_NOMBRE =
      Pattern.compile(
          "\\s+(?:para\\s|de\\s+(?:dama|caballero|hombre|mujer)|unisex|importad[oa]s?|talla)"
              + "|\\s*[,;(]",
          Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

  private static final Pattern ESPACIOS = Pattern.compile("\\s+");

  private TituloDeTenis() {}

  /**
   * @param marca la que se lee en el tenis; nula o en blanco si ninguna
   * @param modelo el que el lector da por seguro; nulo si no. Si empieza por la marca, no se repite
   */
  public static String componer(String marca, String modelo) {
    String limpiaMarca = limpio(marca);
    if (limpiaMarca == null) {
      return BASE;
    }
    String limpioModelo = limpio(modelo);
    if (limpioModelo != null
        && limpioModelo.toLowerCase(Locale.ROOT).startsWith(limpiaMarca.toLowerCase(Locale.ROOT))) {
      limpioModelo = limpio(limpioModelo.substring(limpiaMarca.length()));
    }
    return BASE + " estilo " + limpiaMarca + (limpioModelo == null ? "" : " " + limpioModelo);
  }

  /**
   * El título de un tenis que el extractor tituló a su manera —«Tenis estilo Nike para dama»,
   * «Tenis Adidas Superstar importado»—, con la forma de la tienda. El nombre es lo que sigue a
   * «estilo»; sin «estilo», lo que empieza en la primera marca que el texto nombra. En los dos
   * casos, hasta el género o «importado».
   *
   * @param marcas las que el anuncio nombra para el producto
   * @return el título con la forma de un tenis; nulo si llegó nulo
   */
  public static String desde(String titulo, List<String> marcas) {
    if (titulo == null) {
      return null;
    }
    List<String> lasMarcas = marcas == null ? List.of() : marcas;
    String nombre = null;
    Matcher estilo = ESTILO_Y_RESTO.matcher(titulo);
    if (estilo.find()) {
      nombre = hastaElFin(estilo.group(1));
    } else {
      String enMinusculas = titulo.toLowerCase(Locale.ROOT);
      for (String marca : lasMarcas) {
        if (marca == null || marca.isBlank()) {
          continue;
        }
        int donde = enMinusculas.indexOf(marca.toLowerCase(Locale.ROOT));
        if (donde >= 0) {
          nombre = hastaElFin(titulo.substring(donde));
          break;
        }
      }
    }
    if (nombre == null && !lasMarcas.isEmpty()) {
      nombre = String.join(" - ", lasMarcas);
    }
    return componer(nombre, null);
  }

  /**
   * El texto alternativo de sus fotos en el sitio en inglés: «Imported Nike Air Force 1-style
   * sneakers».
   */
  public static String enIngles(String marca, String modelo) {
    String compuesto = componer(marca, modelo);
    if (compuesto.equals(BASE)) {
      return "Imported sneakers";
    }
    return "Imported " + compuesto.substring((BASE + " estilo ").length()) + "-style sneakers";
  }

  private static String hastaElFin(String resto) {
    Matcher fin = FIN_DEL_NOMBRE.matcher(resto);
    return limpio(fin.find() ? resto.substring(0, fin.start()) : resto);
  }

  private static String limpio(String texto) {
    if (texto == null) {
      return null;
    }
    String limpio = ESPACIOS.matcher(Objects.toString(texto, "")).replaceAll(" ").strip();
    return limpio.isEmpty() ? null : limpio;
  }
}
