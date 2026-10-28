package co.tecnosport.api.domain.proveedores;

import co.tecnosport.api.domain.proveedores.LecturaDeFotos.LecturaDeFoto;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Si un anuncio de un proveedor de calzado no trae un tenis (ADR-0092). La tienda solo vende tenis
 * (ADR-0089), e Imperio Wicho y Tenis Al Pie mandan también chanclas, sandalias y Crocs en el mismo
 * chat. Lo dicen las fotos, que lee el lector de calzado: el extractor del texto no tiene un tipo
 * para la chancla, y la da como «otro».
 *
 * <ul>
 *   <li>Basta una foto con un tenis para que el anuncio siga: con un tenis y una chancla, no se
 *       sabe de cuál es el precio, y lo decide quien revisa.
 *   <li>Sin fotos, o sin lectura de ellas, se descarta: nadie vio que fuera un tenis.
 *   <li>Antes que las fotos, el texto: el anuncio que se llama a sí mismo chola, sandalia o bota, y
 *       no dice «tenis», se descarta sin leer nada (decisión 7).
 * </ul>
 */
public final class CalzadoSinTenis {

  /**
   * El calzado que la tienda no vende, por cómo lo nombra el proveedor. «Playera» es la chancla de
   * playa, como en «Chola Playera Eva».
   */
  private static final Pattern NO_ES_TENIS =
      Pattern.compile(
          "(?<!\\p{L})(?:(chancl(?:a|eta)s?|cholas?|playeras?)|(sandalias?)"
              + "|(crocs?|zuecos?|suecos?)|(botas?|botin|botín|botines)|(pantuflas?|babuchas?))"
              + "(?!\\p{L})",
          Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

  /**
   * Si el texto dice «tenis», manda lo que se vea en las fotos. «Deportivo» no cuenta: Imperio
   * Wicho anuncia la «Sandalia Correa Deportiva».
   */
  private static final Pattern ES_TENIS =
      Pattern.compile(
          "(?<!\\p{L})(?:tenis|zapatillas?|sneakers?)(?!\\p{L})",
          Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

  private static final TipoDeCalzado[] TIPO_DEL_GRUPO = {
    TipoDeCalzado.CHANCLA,
    TipoDeCalzado.SANDALIA,
    TipoDeCalzado.ZUECO,
    TipoDeCalzado.BOTA,
    TipoDeCalzado.OTRO
  };

  private CalzadoSinTenis() {}

  /**
   * Antes de leer el anuncio: si el texto ya dice que no es un tenis, no hace falta gastar el
   * extractor ni el lector de fotos en él.
   *
   * @param texto el del anuncio; puede ser nulo
   * @return por qué se descarta, con el calzado que nombra primero; vacío si no nombra calzado que
   *     no sea un tenis, o si nombra también un tenis
   */
  public static Optional<CierreDePublicacion> porElTexto(String texto) {
    if (texto == null || ES_TENIS.matcher(texto).find()) {
      return Optional.empty();
    }
    Matcher calzado = NO_ES_TENIS.matcher(texto);
    if (!calzado.find()) {
      return Optional.empty();
    }
    for (int grupo = 1; grupo <= TIPO_DEL_GRUPO.length; grupo++) {
      if (calzado.group(grupo) != null) {
        return Optional.of(
            new CierreDePublicacion(
                MotivoDePublicacion.CALZADO_QUE_NO_ES_TENIS, TIPO_DEL_GRUPO[grupo - 1].name()));
      }
    }
    return Optional.empty();
  }

  /**
   * @param lectura la de las fotos del anuncio; nula si no tiene fotos o no se pudieron leer
   * @return por qué se descarta; vacío si alguna foto muestra un tenis
   */
  public static Optional<CierreDePublicacion> en(LecturaDeFotos lectura) {
    if (lectura == null || lectura.fotos().isEmpty()) {
      return Optional.of(CierreDePublicacion.de(MotivoDePublicacion.CALZADO_SIN_FOTOS_LEIDAS));
    }
    List<TipoDeCalzado> tipos =
        lectura.fotos().stream()
            .map(LecturaDeFoto::calzadoOpcional)
            .flatMap(Optional::stream)
            .map(LecturaDeCalzado::tipo)
            .toList();
    if (tipos.contains(TipoDeCalzado.TENIS)) {
      return Optional.empty();
    }
    return Optional.of(
        new CierreDePublicacion(
            MotivoDePublicacion.CALZADO_QUE_NO_ES_TENIS, masVisto(tipos).orElse(null)));
  }

  /** El tipo que más fotos muestran; en un empate, el de la primera. */
  private static Optional<String> masVisto(List<TipoDeCalzado> tipos) {
    Map<TipoDeCalzado, Integer> veces = new LinkedHashMap<>();
    tipos.forEach(tipo -> veces.merge(tipo, 1, Integer::sum));
    TipoDeCalzado mejor = null;
    for (Map.Entry<TipoDeCalzado, Integer> entrada : veces.entrySet()) {
      if (mejor == null || entrada.getValue() > veces.get(mejor)) {
        mejor = entrada.getKey();
      }
    }
    return Optional.ofNullable(mejor).map(Enum::name);
  }
}
