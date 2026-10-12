package co.tecnosport.api.domain.proveedores;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * La etiqueta de diseño que da el lector de fotos, sin los colores. Dos fotos del mismo diseño
 * llevan la misma etiqueta aunque cambie el color: así lo pide el lector, y el reparto agrupa por
 * ella ({@link RepartoDeFotos}). El 11 de octubre de 2026 un tenis en tres combinaciones —negro,
 * negro con la franja blanca, blanco con la franja azul y roja— salió en tres borradores, uno por
 * foto, con el mismo texto y el mismo precio. Esto no depende de que el lector cumpla: lo que solo
 * cambia en el color no separa un diseño de otro.
 *
 * <p>Se quitan los colores, sus matices —«claro», «oscuro»— y el «y» o el «con» que los unía: de
 * «franja azul y roja» queda «franja». Una etiqueta hecha solo de colores no dice ningún diseño, y
 * queda nula.
 */
public final class EtiquetaDeDiseno {

  /** Sin tildes y en minúscula, en singular y en masculino: el plural y el femenino se reducen. */
  private static final Set<String> COLORES =
      Set.of(
          "negro",
          "blanco",
          "beige",
          "gris",
          "azul",
          "verde",
          "menta",
          "rojo",
          "rosado",
          "rosa",
          "amarillo",
          "cafe",
          "cocoa",
          "camel",
          "crema",
          "coral",
          "morado",
          "lila",
          "naranja",
          "fucsia",
          "vino",
          "dorado",
          "plateado",
          "marron",
          "celeste",
          "turquesa",
          "mostaza",
          "oliva",
          "caqui",
          "kaki",
          "nude",
          "hueso",
          "terracota",
          "salmon",
          "violeta",
          "marfil",
          "arena",
          "chocolate",
          "miel",
          "neon",
          "multicolor",
          "bicolor",
          "tricolor");

  private static final Set<String> MATICES =
      Set.of("claro", "oscuro", "pastel", "palo", "color", "tono", "total");

  private static final Set<String> UNIONES = Set.of("y", "e", "con", "de", "en");

  private static final Pattern DIACRITICOS = Pattern.compile("\\p{M}+");

  private static final Pattern SEPARADOR = Pattern.compile("[\\s/,+-]+");

  private EtiquetaDeDiseno() {}

  /** La etiqueta sin sus colores, en minúscula; nula si llegó nula o si solo decía colores. */
  public static String sinColores(String etiqueta) {
    if (etiqueta == null || etiqueta.isBlank()) {
      return null;
    }
    String[] palabras = SEPARADOR.split(etiqueta.strip().toLowerCase(Locale.ROOT));
    boolean[] deColor = new boolean[palabras.length];
    for (int i = 0; i < palabras.length; i++) {
      String base = singular(sinTildes(palabras[i]));
      deColor[i] = COLORES.contains(base) || MATICES.contains(base);
    }
    List<String> quedan = new ArrayList<>(palabras.length);
    for (int i = 0; i < palabras.length; i++) {
      if (palabras[i].isEmpty() || deColor[i]) {
        continue;
      }
      boolean uneColores =
          UNIONES.contains(palabras[i])
              && ((i > 0 && deColor[i - 1]) || (i + 1 < palabras.length && deColor[i + 1]));
      if (!uneColores) {
        quedan.add(palabras[i]);
      }
    }
    return quedan.isEmpty() ? null : String.join(" ", quedan);
  }

  private static String sinTildes(String palabra) {
    return DIACRITICOS.matcher(Normalizer.normalize(palabra, Normalizer.Form.NFD)).replaceAll("");
  }

  /** «negras» → «negro», «rojas» → «rojo», «grises» → «gris», «azules» → «azul». */
  private static String singular(String palabra) {
    String p = palabra;
    if (COLORES.contains(p) || MATICES.contains(p)) {
      return p;
    }
    if (p.endsWith("es") && (COLORES.contains(p.substring(0, p.length() - 2)))) {
      return p.substring(0, p.length() - 2);
    }
    if (p.endsWith("s")) {
      p = p.substring(0, p.length() - 1);
    }
    if (p.endsWith("a") && COLORES.contains(p.substring(0, p.length() - 1) + "o")) {
      return p.substring(0, p.length() - 1) + "o";
    }
    return p;
  }
}
