package co.tecnosport.api.domain.proveedores;

import co.tecnosport.api.domain.compartido.Dinero;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * El precio que un mensaje de proveedor lleva escrito, sin modelo de por medio.
 *
 * <p>Sirve para dos cosas: decidir si un mensaje <b>abre</b> una publicación —un texto con precio
 * es un producto— y contrastar lo que devuelve el extractor, que no se cree a ciegas.
 *
 * <p>Las formas vistas en los chats: {@code 💰 53.000}, {@code 💰*60.000*}, {@code $60.000💰},
 * {@code 62.000_}, {@code PRECIO: $45.000💰}. Lo que las distingue de un teléfono ({@code +57 321
 * 9427252}), de una fecha o de un «3 compartimientos» es la <b>marca</b>: un número solo no es un
 * precio; un número pegado a 💰, a {@code $}, a la palabra PRECIO o con la raya de cierre sí. Con
 * dos precios en el mismo mensaje —«por difusión» y «después de 6»— gana el primero que aparece,
 * que es el que el proveedor pone delante.
 */
public final class PatronDePrecio {

  /** Miles con punto o coma, o cuatro a siete cifras seguidas. */
  private static final String NUMERO = "(\\d{1,3}(?:[.,]\\d{3})+|\\d{4,7})";

  private static final Pattern CANDIDATO =
      Pattern.compile(
          "(?:(💰|\\$|PRECIO[^\\d\\n]{0,12})[\\s*]*\\$?\\s*"
              + NUMERO
              + ")"
              + "|(?:"
              + NUMERO
              + "\\s*\\*?\\s*(💰|_))",
          Pattern.CASE_INSENSITIVE);

  private static final BigDecimal MINIMO = BigDecimal.valueOf(1_000);

  private PatronDePrecio() {}

  public static Optional<Dinero> extraer(String texto) {
    if (texto == null || texto.isBlank()) {
      return Optional.empty();
    }
    Matcher m = CANDIDATO.matcher(texto);
    while (m.find()) {
      String cifra = m.group(2) != null ? m.group(2) : m.group(3);
      BigDecimal valor = new BigDecimal(cifra.replace(".", "").replace(",", ""));
      if (valor.compareTo(MINIMO) >= 0) {
        return Optional.of(Dinero.deCop(valor));
      }
    }
    return Optional.empty();
  }

  public static boolean tienePrecio(String texto) {
    return extraer(texto).isPresent();
  }
}
