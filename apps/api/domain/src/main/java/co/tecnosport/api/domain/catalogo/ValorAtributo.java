package co.tecnosport.api.domain.catalogo;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/**
 * Par atributo-valor de una variante. {@code colorHex} solo tiene sentido cuando {@code
 * atributo.tipo() == COLOR}: el nombre comercial ("Azul marino") va en {@code valor} y el HEX para
 * el selector visual va aparte, ver docs/02-modelo-datos.md.
 */
public record ValorAtributo(Atributo atributo, String valor, String colorHex) {

  private static final Pattern HEX = Pattern.compile("^#[0-9A-Fa-f]{6}$");

  public ValorAtributo {
    if (atributo == null) {
      throw new AtributoInvalidoException("El valor de atributo requiere un atributo.");
    }
    if (valor == null || valor.isBlank()) {
      throw new AtributoInvalidoException(
          "El valor de '" + atributo.nombre() + "' no puede estar vacío.");
    }
    valor = valor.trim();

    if (!atributo.valoresPermitidos().isEmpty() && !atributo.valoresPermitidos().contains(valor)) {
      throw new AtributoInvalidoException(
          "'" + valor + "' no es un valor permitido para '" + atributo.nombre() + "'.");
    }

    if (atributo.tipo() == TipoAtributo.NUMERO) {
      try {
        new BigDecimal(valor);
      } catch (NumberFormatException excepcionOriginal) {
        throw new AtributoInvalidoException(
            "'" + valor + "' no es un número válido para '" + atributo.nombre() + "'.");
      }
    }

    if (colorHex != null && atributo.tipo() != TipoAtributo.COLOR) {
      throw new AtributoInvalidoException("Solo un atributo de tipo COLOR puede llevar colorHex.");
    }
    if (colorHex != null && !HEX.matcher(colorHex).matches()) {
      throw new AtributoInvalidoException("colorHex inválido: " + colorHex);
    }
  }

  public static ValorAtributo de(Atributo atributo, String valor) {
    return new ValorAtributo(atributo, valor, null);
  }

  public static ValorAtributo deColor(Atributo atributo, String nombreComercial, String colorHex) {
    return new ValorAtributo(atributo, nombreComercial, colorHex);
  }
}
