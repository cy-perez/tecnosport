package co.tecnosport.api.domain.compartido;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Qué días cuentan como hábiles, por año.
 *
 * <p>Guarda los festivos <b>por año</b> y no en un conjunto plano porque necesita distinguir dos
 * cosas que un conjunto vacío confunde: "ese año no tiene festivos" —que nunca es cierto en
 * Colombia— y "ese año no se ha cargado". Sin la distinción, un calendario sin cargar haría pasar
 * los festivos por días hábiles y el plazo del retracto vencería antes de tiempo, en contra de
 * quien compra.
 *
 * <p>Los festivos colombianos (Ley 51 de 1983, con los que se trasladan al lunes siguiente) son un
 * dato que este proyecto todavía no tiene cargado. TODO: FESTIVOS_COLOMBIA — cargar el calendario
 * oficial por año. Mientras tanto, {@link #cubre} responde {@code false} y quien pregunte por el
 * plazo recibe {@code INDETERMINADO} en vez de un veredicto inventado.
 *
 * <p>De ese dato pendiente cuelgan ya dos plazos legales y no uno: los cinco días hábiles del
 * retracto y los de respuesta a peticiones, quejas y reclamos. Cargarlo cierra los dos a la vez.
 */
public final class CalendarioHabil {

  private final Map<Integer, Set<LocalDate>> festivosPorAnio;

  private CalendarioHabil(Map<Integer, Set<LocalDate>> festivosPorAnio) {
    this.festivosPorAnio = festivosPorAnio;
  }

  /** El estado de hoy: se conocen los fines de semana, no los festivos. */
  public static CalendarioHabil sinFestivosCargados() {
    return new CalendarioHabil(Map.of());
  }

  public static CalendarioHabil con(Map<Integer, Set<LocalDate>> festivosPorAnio) {
    Objects.requireNonNull(festivosPorAnio, "El mapa de festivos no puede ser nulo.");
    Map<Integer, Set<LocalDate>> copia = new HashMap<>();
    festivosPorAnio.forEach((anio, dias) -> copia.put(anio, Set.copyOf(new HashSet<>(dias))));
    return new CalendarioHabil(Map.copyOf(copia));
  }

  /** ¿Se cargó el calendario de festivos de ese año? */
  public boolean cubre(int anio) {
    return festivosPorAnio.containsKey(anio);
  }

  /**
   * Un día es hábil si no es sábado ni domingo ni festivo conocido. Con el año sin cargar, un
   * festivo se cuenta como hábil — de ahí que nadie deba usar esto sin mirar antes {@link #cubre}.
   */
  public boolean esHabil(LocalDate dia) {
    Objects.requireNonNull(dia, "El día no puede ser nulo.");
    if (dia.getDayOfWeek() == DayOfWeek.SATURDAY || dia.getDayOfWeek() == DayOfWeek.SUNDAY) {
      return false;
    }
    return !festivosPorAnio.getOrDefault(dia.getYear(), Set.of()).contains(dia);
  }

  /**
   * El instante en que se agota un plazo de {@code diasHabiles} contados desde {@code desde}.
   *
   * <p>La cuenta empieza el día <b>siguiente</b> —"dentro de los cinco días hábiles siguientes",
   * dice la norma— y el plazo termina al acabar el último día hábil, no a la hora exacta del hecho:
   * un plazo en días se agota cuando el día se agota.
   *
   * <p>Vive aquí y no dentro de cada plazo concreto porque ya iba por su tercera copia —el
   * retracto, la respuesta a una PQR y la solicitud de reversión cuentan igual— y una cuenta legal
   * repetida en tres archivos se desincroniza el día que cambie una de ellas.
   */
  public Instant limiteTrasDiasHabiles(Instant desde, int diasHabiles) {
    Objects.requireNonNull(desde, "La fecha de inicio del plazo no puede ser nula.");
    LocalDate dia = desde.atZone(ZonaDelNegocio.ZONA).toLocalDate();
    int contados = 0;
    while (contados < diasHabiles) {
      dia = dia.plusDays(1);
      if (esHabil(dia)) {
        contados++;
      }
    }
    return dia.plusDays(1).atStartOfDay(ZonaDelNegocio.ZONA).toInstant();
  }

  /**
   * Si {@code referencia} cayó dentro del plazo.
   *
   * <p>Con los festivos de ese año sin cargar, el límite calculado es el <b>más temprano
   * posible</b> —un festivo solo lo empuja hacia adelante—, así que pasado ese límite no se puede
   * afirmar nada: de ahí {@code INDETERMINADO} en vez de dar un plazo por vencido.
   */
  public VerdictoPlazo verdicto(Instant limite, Instant referencia) {
    Objects.requireNonNull(limite, "El límite no puede ser nulo.");
    Objects.requireNonNull(referencia, "El instante de referencia no puede ser nulo.");
    if (!referencia.isAfter(limite)) {
      return VerdictoPlazo.EN_PLAZO;
    }
    return cubre(limite.atZone(ZonaDelNegocio.ZONA).toLocalDate().getYear())
        ? VerdictoPlazo.VENCIDO
        : VerdictoPlazo.INDETERMINADO;
  }
}
