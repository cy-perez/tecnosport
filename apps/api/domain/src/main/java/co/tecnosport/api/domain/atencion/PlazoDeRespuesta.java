package co.tecnosport.api.domain.atencion;

import co.tecnosport.api.domain.compartido.CalendarioHabil;
import co.tecnosport.api.domain.compartido.VerdictoPlazo;
import co.tecnosport.api.domain.compartido.ZonaDelNegocio;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * El plazo para responder una solicitud de atención, contado en días hábiles desde que <b>llegó</b>
 * y no desde que alguien la registró.
 *
 * <p>Esa distinción es el punto entero: si el plazo corriera desde el registro, bastaría con
 * radicar tarde para no incumplir nunca, y el reloj de la ley no funciona así. La diferencia entre
 * las dos fechas es además lo único que después explica por qué nadie se enteró a tiempo.
 *
 * <p>Pura, como {@code PlazoDeRetracto}: recibe el calendario y los plazos ya resueltos, y no sabe
 * de repositorios ni de configuración. Con los festivos sin cargar devuelve el límite <b>más
 * temprano posible</b> —un festivo solo lo empuja hacia adelante— y por eso el veredicto puede
 * quedar {@code INDETERMINADO} en vez de afirmar un incumplimiento que quizá no ocurrió.
 */
public final class PlazoDeRespuesta {

  private PlazoDeRespuesta() {}

  public static Instant limite(Instant recibidaEn, int diasHabiles, CalendarioHabil calendario) {
    Objects.requireNonNull(recibidaEn, "La fecha en que llegó la solicitud no puede ser nula.");
    Objects.requireNonNull(calendario, "El calendario no puede ser nulo.");
    LocalDate dia = recibidaEn.atZone(ZonaDelNegocio.ZONA).toLocalDate();
    int contados = 0;
    while (contados < diasHabiles) {
      dia = dia.plusDays(1);
      if (calendario.esHabil(dia)) {
        contados++;
      }
    }
    return dia.plusDays(1).atStartOfDay(ZonaDelNegocio.ZONA).toInstant();
  }

  /**
   * {@code respondidaEn} nulo pregunta por lo que falta: si todavía se está a tiempo. Con fecha de
   * respuesta pregunta por lo que ya ocurrió: si se respondió dentro del plazo.
   */
  public static VerdictoPlazo verdicto(
      Instant limite, Instant referencia, CalendarioHabil calendario) {
    Objects.requireNonNull(limite, "El límite no puede ser nulo.");
    Objects.requireNonNull(referencia, "El instante de referencia no puede ser nulo.");
    if (!referencia.isAfter(limite)) {
      return VerdictoPlazo.EN_PLAZO;
    }
    return calendario.cubre(limite.atZone(ZonaDelNegocio.ZONA).toLocalDate().getYear())
        ? VerdictoPlazo.VENCIDO
        : VerdictoPlazo.INDETERMINADO;
  }
}
