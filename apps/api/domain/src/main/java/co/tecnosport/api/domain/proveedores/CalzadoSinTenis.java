package co.tecnosport.api.domain.proveedores;

import co.tecnosport.api.domain.proveedores.LecturaDeFotos.LecturaDeFoto;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
 * </ul>
 */
public final class CalzadoSinTenis {

  private CalzadoSinTenis() {}

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
