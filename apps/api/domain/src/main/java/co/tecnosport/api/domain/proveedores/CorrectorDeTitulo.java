package co.tecnosport.api.domain.proveedores;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lo que el título de un producto de proveedor corrige sin depender del modelo: los espacios de más
 * y la palabra «body», que en español se escribe «bodi» —«bodis» en plural— (decidido por el
 * negocio el 3 de octubre de 2026). El prompt pide lo mismo; esto existe para que no dependa de que
 * el modelo lo recuerde.
 *
 * <p>Conserva la mayúscula inicial de la palabra que corrige, y nada más: «Body Herraje» queda
 * «Bodi Herraje», y la capitalización del resto la decide el extractor, que sabe qué es marca.
 */
public final class CorrectorDeTitulo {

  private static final Pattern BODY =
      Pattern.compile("\\b(b)od(?:y|i)(s|es)?\\b", Pattern.CASE_INSENSITIVE);
  private static final Pattern ESPACIOS = Pattern.compile("\\s+");

  private CorrectorDeTitulo() {}

  /**
   * @return el título corregido, o nulo si llegó nulo
   */
  public static String corregir(String titulo) {
    if (titulo == null) {
      return null;
    }
    String unEspacio = ESPACIOS.matcher(titulo.strip()).replaceAll(" ");
    Matcher m = BODY.matcher(unEspacio);
    StringBuilder corregido = new StringBuilder();
    while (m.find()) {
      boolean mayuscula = Character.isUpperCase(m.group(1).charAt(0));
      String palabra = (mayuscula ? "B" : "b") + "odi" + (m.group(2) == null ? "" : "s");
      m.appendReplacement(corregido, palabra);
    }
    m.appendTail(corregido);
    return corregido.toString();
  }
}
