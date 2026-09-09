package co.tecnosport.api.domain.retracto;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;

/**
 * Los cinco días hábiles del derecho de retracto (Ley 1480 de 2011, art. 47), contados desde la
 * entrega y en la zona horaria del negocio.
 *
 * <p>Pura, como {@link co.tecnosport.api.domain.pedido.PoliticaContraentrega}: recibe el calendario
 * ya resuelto y no sabe de repositorios ni de configuración.
 *
 * <p>La cuenta empieza el día <b>siguiente</b> a la entrega —"dentro de los cinco días hábiles
 * siguientes", dice la norma— y el plazo termina al acabar el quinto día hábil, no a la hora exacta
 * de la entrega: un plazo en días se agota cuando el día se agota.
 */
public final class PlazoDeRetracto {

  /** El negocio opera en Medellín y los plazos legales se cuentan con su calendario. */
  public static final ZoneId ZONA = ZoneId.of("America/Bogota");

  private static final int DIAS_HABILES = 5;

  private PlazoDeRetracto() {}

  /**
   * El último instante para retractarse, calculado con el calendario dado. Con los festivos sin
   * cargar devuelve el límite <b>más temprano posible</b>: los festivos solo lo empujan hacia
   * adelante, nunca lo adelantan.
   */
  public static Instant limite(Instant entregadoEn, CalendarioHabil calendario) {
    Objects.requireNonNull(entregadoEn, "La fecha de entrega no puede ser nula.");
    Objects.requireNonNull(calendario, "El calendario no puede ser nulo.");
    LocalDate dia = entregadoEn.atZone(ZONA).toLocalDate();
    int habilesContados = 0;
    while (habilesContados < DIAS_HABILES) {
      dia = dia.plusDays(1);
      if (calendario.esHabil(dia)) {
        habilesContados++;
      }
    }
    return dia.plusDays(1).atStartOfDay(ZONA).toInstant();
  }

  /**
   * El veredicto, que es lo que el panel necesita mostrar. Nunca bloquea nada: radicar un retracto
   * fuera de plazo sigue siendo posible —puede haber un acuerdo comercial, o una garantía por
   * detrás— y quien decide es una persona, con este dato delante.
   */
  public static VerdictoPlazo verdicto(
      Instant entregadoEn, Instant ahora, CalendarioHabil calendario) {
    Objects.requireNonNull(ahora, "El instante actual no puede ser nulo.");
    Instant limite = limite(entregadoEn, calendario);
    if (!ahora.isAfter(limite)) {
      return VerdictoPlazo.EN_PLAZO;
    }
    return calendario.cubre(limite.atZone(ZONA).toLocalDate().getYear())
        ? VerdictoPlazo.VENCIDO
        : VerdictoPlazo.INDETERMINADO;
  }
}
