package co.tecnosport.api.domain.catalogo;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Una porción del círculo de la muestra: un color liso —{@code patron} nulo y un solo color— o un
 * patrón con los colores que lo componen, en su orden.
 */
public record ParteDeMuestra(PatronDeColor patron, List<String> colores) {

  private static final Pattern HEX = Pattern.compile("^#[0-9A-Fa-f]{6}$");

  public ParteDeMuestra {
    colores = colores == null ? List.of() : List.copyOf(colores);
    if (colores.isEmpty()) {
      throw new ExcepcionDeDominio("Una parte de la muestra necesita al menos un color.");
    }
    if (patron == null && colores.size() != 1) {
      throw new ExcepcionDeDominio("Un color liso es un solo color.");
    }
    if (patron != null && colores.size() < 2) {
      throw new ExcepcionDeDominio("Un patrón se dibuja con al menos dos colores.");
    }
    for (String color : colores) {
      if (color == null || !HEX.matcher(color).matches()) {
        throw new ExcepcionDeDominio("Color inválido en la muestra: " + color);
      }
    }
  }

  public static ParteDeMuestra lisa(String hex) {
    return new ParteDeMuestra(null, List.of(hex));
  }

  public boolean esLisa() {
    return patron == null;
  }
}
