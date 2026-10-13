package co.tecnosport.api.domain.proveedores;

import co.tecnosport.api.domain.compartido.Dinero;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Si un precio está escrito en el texto de un anuncio. Es la forma de no creerle al modelo un
 * precio que el proveedor no escribió: en las faldas de La Riverah con «Set falda + básica $88.000»
 * el modelo puso además una blusa a 38.100, la resta del set y la falda (12 de octubre de 2026).
 *
 * <p>Cuenta como escrito el valor completo —«88.000», «$ 88.000», «88000»—, los miles con la
 * palabra —«35mil», «35 mil»— y los miles pegados a 💲 —«💲124»—, nunca como parte de otra cifra.
 */
public final class PrecioEscrito {

  private static final Pattern NO_CUENTA = Pattern.compile("[\\s.,$]+");

  private PrecioEscrito() {}

  public static boolean en(Dinero precio, String texto) {
    if (precio == null || texto == null) {
      return false;
    }
    String compacto = NO_CUENTA.matcher(texto.toLowerCase(Locale.ROOT)).replaceAll("");
    long valor = precio.valor().longValueExact();
    if (aparece("(?<!\\d)" + valor + "(?!\\d)", compacto)) {
      return true;
    }
    if (valor % 1000 != 0) {
      return false;
    }
    long miles = valor / 1000;
    return aparece("(?<!\\d)" + miles + "mil", compacto)
        || aparece("💲" + miles + "(?!\\d)", compacto);
  }

  private static boolean aparece(String patron, String texto) {
    return Pattern.compile(patron).matcher(texto).find();
  }
}
