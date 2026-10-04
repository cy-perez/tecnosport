package co.tecnosport.api.domain.catalogo;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/**
 * Par atributo-valor de una variante. {@code colorHex} y {@code muestra} solo tienen sentido cuando
 * {@code atributo.tipo() == COLOR}: el nombre comercial ("Azul marino", "Negro / Rojo") va en
 * {@code valor}, y lo que pinta el círculo del selector va aparte, ver docs/02-modelo-datos.md.
 *
 * <p>{@code muestra} es lo que se pinta —una a tres porciones, lisas o con patrón— y {@code
 * colorHex} es su primer color: el que ya guardaba la columna antes de las combinaciones (4 de
 * octubre de 2026), y lo que sigue leyendo quien no sabe de porciones. Se dan uno u otro, o los dos
 * de acuerdo; el otro se deduce.
 */
public record ValorAtributo(
    Atributo atributo, String valor, String colorHex, MuestraDeColor muestra) {

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

    if ((colorHex != null || muestra != null) && atributo.tipo() != TipoAtributo.COLOR) {
      throw new AtributoInvalidoException("Solo un atributo de tipo COLOR puede llevar colorHex.");
    }
    if (colorHex != null && !HEX.matcher(colorHex).matches()) {
      throw new AtributoInvalidoException("colorHex inválido: " + colorHex);
    }
    if (muestra == null && colorHex != null) {
      muestra = MuestraDeColor.lisa(colorHex);
    } else if (muestra != null && colorHex == null) {
      colorHex = muestra.primerHex();
    } else if (muestra != null && !muestra.primerHex().equalsIgnoreCase(colorHex)) {
      throw new AtributoInvalidoException(
          "colorHex tiene que ser el primer color de la muestra: " + colorHex);
    }
  }

  public ValorAtributo(Atributo atributo, String valor, String colorHex) {
    this(atributo, valor, colorHex, null);
  }

  public static ValorAtributo de(Atributo atributo, String valor) {
    return new ValorAtributo(atributo, valor, null, null);
  }

  public static ValorAtributo deColor(Atributo atributo, String nombreComercial, String colorHex) {
    return new ValorAtributo(atributo, nombreComercial, colorHex, null);
  }

  /** Un color con su muestra, que puede combinar hasta tres porciones. */
  public static ValorAtributo deColor(
      Atributo atributo, String nombreComercial, MuestraDeColor muestra) {
    return new ValorAtributo(atributo, nombreComercial, null, muestra);
  }
}
