package co.tecnosport.api.domain.proveedores;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Un título reducido a lo que lo identifica: minúsculas, sin tildes, sin emojis ni signos, con un
 * solo espacio entre palabras. «Bolso de dama mediano 👜» y «BOLSO DE DAMA MEDIANO» son el mismo
 * producto, y la huella se calcula sobre esto y no sobre lo que escribió el proveedor ese día.
 */
public final class NormalizadorDeTitulo {

  private static final Pattern DIACRITICOS = Pattern.compile("\\p{M}+");
  private static final Pattern NO_ALFANUMERICO = Pattern.compile("[^a-z0-9]+");

  /**
   * «Bodi» y «body» son la misma prenda: el título se corrige a «bodi» desde el 3 de octubre de
   * 2026 ({@link CorrectorDeTitulo}), y sin esto los productos aprobados antes con «Body» dejaban
   * de reconocerse en su siguiente anuncio y volvían como nuevos.
   */
  private static final Pattern BODI = Pattern.compile("\\bbod(?:y|i)(?:s|es)?\\b");

  private NormalizadorDeTitulo() {}

  public static String normalizar(String titulo) {
    if (titulo == null) {
      return "";
    }
    String sinTildes =
        DIACRITICOS.matcher(Normalizer.normalize(titulo, Normalizer.Form.NFD)).replaceAll("");
    String plano =
        NO_ALFANUMERICO.matcher(sinTildes.toLowerCase(Locale.ROOT)).replaceAll(" ").strip();
    return BODI.matcher(plano).replaceAll("body");
  }
}
