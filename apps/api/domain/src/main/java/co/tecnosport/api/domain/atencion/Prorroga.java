package co.tecnosport.api.domain.atencion;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import java.time.Instant;
import java.util.Objects;

/**
 * La prórroga del plazo de respuesta, con lo único que la hace válida: el aviso al interesado.
 *
 * <p>La ley no concede días extra por pedirlos. Los concede si se informa al interesado, con sus
 * motivos, <b>antes de que venza el plazo inicial</b> — y la política de datos publicada repite esa
 * condición palabra por palabra. Una prórroga que nadie avisó no es una prórroga: es un
 * incumplimiento con más días encima. Por eso {@code avisadaEn} y {@code motivo} son obligatorios y
 * no se pueden dejar para después.
 */
public record Prorroga(Instant otorgadaEn, String otorgadaPor, String motivo, Instant avisadaEn) {

  public Prorroga {
    Objects.requireNonNull(otorgadaEn, "La fecha de la prórroga no puede ser nula.");
    Objects.requireNonNull(avisadaEn, "Una prórroga sin aviso al interesado no es una prórroga.");
    if (otorgadaPor == null || otorgadaPor.isBlank()) {
      throw new ExcepcionDeDominio("Quien otorga una prórroga no puede quedar en blanco.");
    }
    if (motivo == null || motivo.isBlank()) {
      throw new ExcepcionDeDominio(
          "Una prórroga necesita sus motivos: la ley obliga a informarlos al interesado.");
    }
  }
}
