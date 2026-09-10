package co.tecnosport.api.domain.atencion;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import java.time.Instant;
import java.util.Objects;

/**
 * La constancia de que se respondió: cuándo, quién y qué se contestó, en resumen.
 *
 * <p>{@code resumen} es obligatorio y corto a propósito. No pretende guardar el correo entero —eso
 * vive en el buzón— sino dejar en el expediente qué se le dijo al interesado, que es lo que hay que
 * poder mostrar el día que alguien pregunte si la respuesta fue de fondo o una acusación de recibo.
 */
public record Respuesta(Instant respondidaEn, String respondidaPor, String resumen) {

  public Respuesta {
    Objects.requireNonNull(respondidaEn, "La fecha de la respuesta no puede ser nula.");
    if (respondidaPor == null || respondidaPor.isBlank()) {
      throw new ExcepcionDeDominio("Quien responde no puede quedar en blanco.");
    }
    if (resumen == null || resumen.isBlank()) {
      throw new ExcepcionDeDominio("Una respuesta sin contenido no es una respuesta.");
    }
  }
}
