package co.tecnosport.api.domain.retracto;

import java.time.DayOfWeek;
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
}
