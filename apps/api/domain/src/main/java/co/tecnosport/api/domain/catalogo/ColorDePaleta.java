package co.tecnosport.api.domain.catalogo;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Un color de la paleta con que se marca el tono de cada foto al revisar un borrador. El nombre es
 * el valor del atributo Color de la variante; el HEX, lo que pinta la muestra en la tarjeta y en la
 * ficha. Es un dato del producto y no del sistema visual: por eso vive en la base y no en los
 * tokens de la marca.
 *
 * <p>Un diseño —multicolor, estampado, animal print— lleva además su {@code patron} y los colores
 * que lo componen; su {@code hex} es el primero de ellos, el que guarda {@code color_hex}.
 *
 * @param coloresDelPatron vacío en un color liso
 */
public record ColorDePaleta(
    UUID id,
    String nombre,
    String nombreEn,
    String hex,
    int orden,
    PatronDeColor patron,
    List<String> coloresDelPatron) {

  private static final Pattern HEX = Pattern.compile("^#[0-9A-F]{6}$");

  public ColorDePaleta {
    Objects.requireNonNull(id, "El color necesita id.");
    if (nombre == null || nombre.isBlank() || nombreEn == null || nombreEn.isBlank()) {
      throw new ExcepcionDeDominio("El color necesita su nombre en español y en inglés.");
    }
    if (hex == null || !HEX.matcher(hex).matches()) {
      throw new ExcepcionDeDominio("El color tiene que ser un HEX #RRGGBB en mayúsculas.");
    }
    nombre = nombre.strip();
    nombreEn = nombreEn.strip();
    coloresDelPatron = coloresDelPatron == null ? List.of() : List.copyOf(coloresDelPatron);
    if ((patron == null) != coloresDelPatron.isEmpty()) {
      throw new ExcepcionDeDominio(
          "Un patrón lleva sus colores, y un color liso no lleva ninguno.");
    }
  }

  /** Un color liso. */
  public ColorDePaleta(UUID id, String nombre, String nombreEn, String hex, int orden) {
    this(id, nombre, nombreEn, hex, orden, null, List.of());
  }

  /** La porción de la muestra que este color pinta. */
  public ParteDeMuestra parte() {
    return patron == null ? ParteDeMuestra.lisa(hex) : new ParteDeMuestra(patron, coloresDelPatron);
  }
}
