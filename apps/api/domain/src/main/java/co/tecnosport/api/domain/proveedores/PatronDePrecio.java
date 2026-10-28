package co.tecnosport.api.domain.proveedores;

import co.tecnosport.api.domain.compartido.Dinero;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
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
 * {@code 62.000_}, {@code PRECIO: $45.000💰}, {@code 🤑🤑*55.000*}, {@code 🎽55.000~~} y {@code
 * 💲124}. Lo que las distingue de un teléfono ({@code +57 321 9427252}), de una fecha o de un «3
 * compartimientos» es la <b>marca</b>: un número solo no es un precio; un número pegado a 💰, 💲,
 * 🤑, a {@code $}, a la palabra PRECIO o con la raya de cierre sí. Con dos precios en el mismo
 * mensaje —«por difusión» y «después de 6»— gana el primero que aparece, que es el que el proveedor
 * pone delante.
 *
 * <h2>Los precios en miles</h2>
 *
 * <p>Violeta escribe {@code 💲124} por 124.000 (exportación del 2 de octubre de 2026). Dos o tres
 * cifras sueltas se leen en miles, pero <b>solo pegadas a 💲 o a {@code $}</b>, que es donde se
 * vieron: detrás de la palabra PRECIO lo que viene suele ser una cantidad —«precio x 12 unidades»—,
 * sin marca es una talla, y {@code 💰 500} se decidió antes que no es un precio. El {@code $} entró
 * el 28 de octubre de 2026, con el {@code $ 39} de un body de Violeta al lado del {@code 💲78} de
 * su pantalón: leyendo solo el segundo, el anuncio de dos prendas salía con un precio y ninguno se
 * contrastaba. Si otro proveedor abrevia con otra marca, se agrega con su ejemplo. Una sola cifra
 * no es un precio de nada que se venda aquí.
 *
 * <h2>El cierre doble</h2>
 *
 * <p>{@code ~~} cierra el precio igual que {@code _}, y solo doble: una virgulilla sola es la
 * tachadura de WhatsApp ({@code ~70.000~}), que marca justo el precio que ya no vale.
 *
 * <h2>Lo tachado no se lee</h2>
 *
 * <p>La Riverah tacha el precio por mayor y escribe debajo el que vale: {@code ~~ PRECIO x
 * MAYOR🤑99.900🥳~~~} y luego {@code Súper descuento $69.900} (exportación del 9 de octubre de
 * 2026). Leído tal cual, el primer precio era el tachado. Por eso, antes de buscar, se borra todo
 * tramo tachado: el que abre con virgulillas donde no las precede una letra ni una cifra —al
 * principio de la línea, detrás de un espacio o de un emoji— y cierra con virgulillas en la misma
 * línea. El emoji entró el 28 de octubre de 2026: {@code 🤑~$65.000~} y debajo {@code Promo
 * $59.900}, y el jean salía a 65.000. El cierre doble de arriba no se confunde con eso porque va
 * pegado al número: {@code 🎽55.000~~} no abre nada.
 *
 * <h2>El precio por docena no es el de un par</h2>
 *
 * <p>Imperio Wicho escribe {@code 📦Docena *$120,000*} y debajo {@code 💰*$12,000*}, o {@code Detal
 * 💰*$20,000*} (exportación del 27 de octubre de 2026). La tienda compra de a uno: si el texto trae
 * un precio fuera de una línea que nombra la docena, los de esas líneas no cuentan. Si solo trae el
 * de la docena, se queda: es el único precio que hay, y quien revisa lo ve.
 *
 * <h2>El precio por mayor manda</h2>
 *
 * <p>Tenis Al Pie escribe los dos: {@code Detal :$180.000} y debajo {@code X mayor 💰135}
 * (exportación del 23 de octubre de 2026). El primero es lo que él sugiere cobrarle al público; el
 * que paga la tienda es el segundo. Por eso, si el texto rotula algún precio «x mayor», «por mayor»
 * o «al por mayor», <b>solo cuentan los rotulados</b>, en su orden, y el de detal no entra ni como
 * precio ni en la lista. Detrás del rótulo, dos o tres cifras también se leen en miles, como detrás
 * de 💲: el rótulo ya dice que lo que sigue es un precio. «Mayorista» no es el rótulo.
 */
public final class PatronDePrecio {

  /** Miles con punto o coma, o cuatro a siete cifras seguidas. */
  private static final String NUMERO = "(\\d{1,3}(?:[.,]\\d{3})+|\\d{4,7})";

  /** Dos o tres cifras que no siguen en más cifras: un precio escrito en miles. */
  private static final String EN_MILES = "(\\d{2,3})(?!\\d|[.,]\\d)";

  private static final String SIMBOLO = "(?:💰|💲|🤑|\\$)";

  private static final Pattern CANDIDATO =
      Pattern.compile(
          "(?:"
              + SIMBOLO
              + "[\\s*]*\\$?\\s*"
              + NUMERO
              + ")"
              + "|(?:(?:💲|\\$)\\s*"
              + EN_MILES
              + ")"
              + "|(?:PRECIO[^\\d\\n]{0,12}[\\s*]*\\$?\\s*"
              + NUMERO
              + ")"
              + "|(?:"
              + NUMERO
              + "\\s*\\*?\\s*(?:💰|_|~~))",
          Pattern.CASE_INSENSITIVE);

  /**
   * El precio rotulado por mayor: el rótulo, hasta doce caracteres sin cifras en la misma línea
   * —«unidad 💰», «* $»— y el número completo o en miles.
   */
  private static final Pattern POR_MAYOR =
      Pattern.compile(
          "(?<!\\p{L})(?:x|por)\\s*mayor(?!\\p{L})[^\\d\\n]{0,12}?(?:"
              + NUMERO
              + "|"
              + EN_MILES
              + ")",
          Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

  /** Un tramo tachado de WhatsApp con alguna cifra dentro, sin salir de su línea. */
  private static final Pattern TACHADO =
      Pattern.compile("(?<![\\p{L}\\p{N}])~+[^~\\n]*\\d[^~\\n]*~+");

  /** La línea nombra la docena: «📦Docena *$120,000*», «x docena», «docenas». */
  private static final Pattern DOCENA =
      Pattern.compile(
          "(?<!\\p{L})docenas?(?!\\p{L})", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

  private static final BigDecimal MINIMO = BigDecimal.valueOf(1_000);

  private static final BigDecimal MIL = BigDecimal.valueOf(1_000);

  private PatronDePrecio() {}

  public static Optional<Dinero> extraer(String texto) {
    if (texto == null || texto.isBlank()) {
      return Optional.empty();
    }
    List<Dinero> todos = extraerTodos(texto);
    return todos.isEmpty() ? Optional.empty() : Optional.of(todos.getFirst());
  }

  /**
   * Todos los precios del texto, en el orden en que aparecen. Es con lo que se contrasta un mensaje
   * de varios productos: el i-ésimo producto que leyó el extractor contra el i-ésimo precio.
   */
  public static List<Dinero> extraerTodos(String texto) {
    if (texto == null || texto.isBlank()) {
      return List.of();
    }
    String legible = sinTachado(texto);
    List<Dinero> porMayor = new ArrayList<>();
    Matcher rotulado = POR_MAYOR.matcher(legible);
    while (rotulado.find()) {
      BigDecimal valor =
          rotulado.group(1) != null
              ? new BigDecimal(rotulado.group(1).replace(".", "").replace(",", ""))
              : new BigDecimal(rotulado.group(2)).multiply(MIL);
      if (valor.compareTo(MINIMO) >= 0) {
        porMayor.add(Dinero.deCop(valor));
      }
    }
    if (!porMayor.isEmpty()) {
      return List.copyOf(porMayor);
    }
    List<Dinero> precios = new ArrayList<>();
    List<Dinero> deUno = new ArrayList<>();
    Matcher m = CANDIDATO.matcher(legible);
    while (m.find()) {
      BigDecimal valor = valorDe(m);
      if (valor.compareTo(MINIMO) >= 0) {
        precios.add(Dinero.deCop(valor));
        if (!DOCENA.matcher(lineaDe(legible, m.start())).find()) {
          deUno.add(Dinero.deCop(valor));
        }
      }
    }
    return List.copyOf(deUno.isEmpty() ? precios : deUno);
  }

  public static boolean tienePrecio(String texto) {
    return extraer(texto).isPresent();
  }

  /** El texto con cada tramo tachado cambiado por un espacio: lo tachado ya no vale. */
  static String sinTachado(String texto) {
    return TACHADO.matcher(texto).replaceAll(" ");
  }

  private static String lineaDe(String texto, int posicion) {
    int desde = texto.lastIndexOf('\n', posicion) + 1;
    int hasta = texto.indexOf('\n', posicion);
    return texto.substring(desde, hasta < 0 ? texto.length() : hasta);
  }

  private static BigDecimal valorDe(Matcher m) {
    if (m.group(2) != null) {
      return new BigDecimal(m.group(2)).multiply(MIL);
    }
    String cifra = m.group(1) != null ? m.group(1) : m.group(3) != null ? m.group(3) : m.group(4);
    return new BigDecimal(cifra.replace(".", "").replace(",", ""));
  }
}
