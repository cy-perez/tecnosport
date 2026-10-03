package co.tecnosport.api.domain.catalogo;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Un color de la paleta con que se marca el tono de cada foto al revisar un borrador. El nombre es
 * el valor del atributo Color de la variante; el HEX, lo que pinta la muestra en la tarjeta y en la
 * ficha. Es un dato del producto y no del sistema visual: por eso vive en la base y no en los
 * tokens de la marca.
 */
public record ColorDePaleta(UUID id, String nombre, String nombreEn, String hex, int orden) {

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
  }
}
