package co.tecnosport.api.domain.compartido;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Qué días cuentan como hábiles, por año.
 *
 * <p>Pregunta los festivos <b>por año</b>, y la respuesta es un {@code Optional}, porque hay que
 * distinguir dos cosas que un conjunto vacío confunde: "ese año no tiene festivos" —que nunca es
 * cierto en Colombia— y "ese año no se conoce". Sin la distinción, un calendario sin resolver haría
 * pasar los festivos por días hábiles y el plazo del retracto vencería antes de tiempo, en contra
 * de quien compra.
 *
 * <p>En producción se construye con {@link #calculado()}, que resuelve cualquier año con {@link
 * FestivosColombia}. Los otros dos constructores existen para las pruebas y para dejar dicho, en
 * código, qué pasa cuando el calendario no se conoce: {@link #cubre} responde {@code false} y quien
 * pregunte por el plazo recibe {@code INDETERMINADO} en vez de un veredicto inventado.
 *
 * <p>De este calendario cuelgan tres plazos legales: los cinco días hábiles del retracto, los de
 * respuesta a peticiones, quejas y reclamos, y los de la solicitud de reversión del pago. Los tres
 * daban {@code INDETERMINADO} pasado el límite mientras los festivos fueron un dato pendiente.
 */
public final class CalendarioHabil {

  /**
   * De dónde salen los festivos de un año. Vacío significa "ese año no se conoce", que es distinto
   * de "ese año no tiene festivos" — lo segundo no es cierto de ningún año en Colombia.
   */
  @FunctionalInterface
  public interface Festivos {
    Optional<Set<LocalDate>> delAnio(int anio);
  }

  private final Festivos festivos;

  private CalendarioHabil(Festivos festivos) {
    this.festivos = festivos;
  }

  /**
   * El calendario de producción: cualquier año, calculado con {@link FestivosColombia}.
   *
   * <p>Memoriza lo ya calculado porque {@link #limiteTrasDiasHabiles} pregunta por un día tras
   * otro, y recalcular diecinueve fechas en cada vuelta del bucle no aporta nada. El mapa es
   * concurrente porque el {@code bean} es único y lo comparten todas las peticiones.
   */
  public static CalendarioHabil calculado() {
    Map<Integer, Set<LocalDate>> memoria = new ConcurrentHashMap<>();
    return new CalendarioHabil(
        anio -> Optional.of(memoria.computeIfAbsent(anio, FestivosColombia::delAnio)));
  }

  /** Solo los fines de semana: el calendario que no sabe de festivos. */
  public static CalendarioHabil sinFestivosCargados() {
    return new CalendarioHabil(anio -> Optional.empty());
  }

  public static CalendarioHabil con(Map<Integer, Set<LocalDate>> festivosPorAnio) {
    Objects.requireNonNull(festivosPorAnio, "El mapa de festivos no puede ser nulo.");
    Map<Integer, Set<LocalDate>> copia = new HashMap<>();
    festivosPorAnio.forEach((anio, dias) -> copia.put(anio, Set.copyOf(new HashSet<>(dias))));
    Map<Integer, Set<LocalDate>> inmutable = Map.copyOf(copia);
    return new CalendarioHabil(anio -> Optional.ofNullable(inmutable.get(anio)));
  }

  /** ¿Se conoce el calendario de festivos de ese año? */
  public boolean cubre(int anio) {
    return festivos.delAnio(anio).isPresent();
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
    return !festivos.delAnio(dia.getYear()).orElseGet(Set::of).contains(dia);
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
