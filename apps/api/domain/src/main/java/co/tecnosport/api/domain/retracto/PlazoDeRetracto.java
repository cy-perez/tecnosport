package co.tecnosport.api.domain.retracto;

import co.tecnosport.api.domain.compartido.CalendarioHabil;
import co.tecnosport.api.domain.compartido.VerdictoPlazo;
import co.tecnosport.api.domain.compartido.ZonaDelNegocio;
import java.time.Instant;
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

  /**
   * Alias del huso del negocio, conservado porque medio proyecto ya cuenta plazos con {@code
   * PlazoDeRetracto.ZONA} y renombrarlo en todos lados no aporta nada. La definición vive en {@link
   * ZonaDelNegocio}, que es donde la comparten los demás plazos.
   */
  public static final ZoneId ZONA = ZonaDelNegocio.ZONA;

  private static final int DIAS_HABILES = 5;

  private PlazoDeRetracto() {}

  /**
   * El último instante para retractarse, calculado con el calendario dado.
   *
   * <p>Con un calendario que no conozca el año devuelve el límite <b>más temprano posible</b>,
   * porque los festivos solo lo empujan hacia adelante y nunca lo adelantan. En producción eso ya
   * no pasa —{@code CalendarioHabil.calculado()} resuelve cualquier año desde {@code ADR-0024}— y
   * el caso sobrevive por dos motivos: las pruebas lo usan para fijar el comportamiento, y el
   * dominio no tiene por qué asumir que quien le pasa un calendario sabe de festivos.
   */
  public static Instant limite(Instant entregadoEn, CalendarioHabil calendario) {
    Objects.requireNonNull(entregadoEn, "La fecha de entrega no puede ser nula.");
    Objects.requireNonNull(calendario, "El calendario no puede ser nulo.");
    return calendario.limiteTrasDiasHabiles(entregadoEn, DIAS_HABILES);
  }

  /**
   * El veredicto, que es lo que el panel necesita mostrar. Nunca bloquea nada: radicar un retracto
   * fuera de plazo sigue siendo posible —puede haber un acuerdo comercial, o una garantía por
   * detrás— y quien decide es una persona, con este dato delante.
   */
  public static VerdictoPlazo verdicto(
      Instant entregadoEn, Instant ahora, CalendarioHabil calendario) {
    Objects.requireNonNull(ahora, "El instante actual no puede ser nulo.");
    return calendario.verdicto(limite(entregadoEn, calendario), ahora);
  }
}
