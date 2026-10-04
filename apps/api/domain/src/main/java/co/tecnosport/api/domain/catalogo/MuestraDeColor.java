package co.tecnosport.api.domain.catalogo;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Lo que pinta el círculo de un color: una a tres porciones, en el orden en que se eligieron. Una
 * camiseta negra y roja es «Negro / Rojo» y su círculo va mitad negro —a la izquierda— y mitad rojo
 * (decidido por el negocio el 4 de octubre de 2026). Más de tres porciones no se distinguen en un
 * círculo pequeño: para eso está «Multicolor».
 *
 * @param partes en el orden en que se eligieron los colores
 */
public record MuestraDeColor(List<ParteDeMuestra> partes) {

  /** Cuántos colores se combinan como máximo en una variante. */
  public static final int MAXIMO_DE_PARTES = 3;

  /** Cómo se escribe una combinación en el valor del atributo: «Negro / Rojo». */
  public static final String SEPARADOR = " / ";

  private static final Pattern PARTIR = Pattern.compile("\\s*/\\s*");
  private static final Pattern DIACRITICOS = Pattern.compile("\\p{M}+");

  public MuestraDeColor {
    partes = partes == null ? List.of() : List.copyOf(partes);
    if (partes.isEmpty()) {
      throw new ExcepcionDeDominio("Una muestra de color necesita al menos un color.");
    }
    if (partes.size() > MAXIMO_DE_PARTES) {
      throw new ExcepcionDeDominio(
          "Se combinan hasta " + MAXIMO_DE_PARTES + " colores; para más, usa «Multicolor».");
    }
  }

  public static MuestraDeColor lisa(String hex) {
    return new MuestraDeColor(List.of(ParteDeMuestra.lisa(hex)));
  }

  /** El primer color de la primera porción: el HEX que sigue guardando {@code color_hex}. */
  public String primerHex() {
    return partes.get(0).colores().get(0);
  }

  /**
   * La muestra del valor de un color, armada con la paleta: «Negro / Rojo» son dos porciones con el
   * HEX de cada uno. Vacío si alguna parte del nombre no está en la paleta —un color escrito a mano
   * no tiene con qué pintarse—.
   *
   * @throws ExcepcionDeDominio si el nombre combina más colores de los que caben
   */
  public static Optional<MuestraDeColor> componer(String valor, List<ColorDePaleta> paleta) {
    if (valor == null || valor.isBlank() || paleta == null) {
      return Optional.empty();
    }
    List<ParteDeMuestra> partes = new ArrayList<>();
    for (String nombre : PARTIR.split(valor.strip())) {
      Optional<ColorDePaleta> color =
          paleta.stream().filter(c -> plano(c.nombre()).equals(plano(nombre))).findFirst();
      if (color.isEmpty()) {
        return Optional.empty();
      }
      partes.add(color.get().parte());
    }
    return Optional.of(new MuestraDeColor(partes));
  }

  private static String plano(String texto) {
    return DIACRITICOS
        .matcher(Normalizer.normalize(texto.strip(), Normalizer.Form.NFD))
        .replaceAll("")
        .toLowerCase(Locale.ROOT);
  }
}
