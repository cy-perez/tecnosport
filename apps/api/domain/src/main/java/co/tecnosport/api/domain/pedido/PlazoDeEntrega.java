package co.tecnosport.api.domain.pedido;

import co.tecnosport.api.domain.compartido.VerdictoPlazo;
import co.tecnosport.api.domain.compartido.ZonaDelNegocio;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Los treinta días calendario para entregar (Ley 1480 de 2011, art. 18), contados desde que el
 * contrato quedó en firme y en la zona horaria del negocio.
 *
 * <p>Es el plazo que los términos publicados prometen: "No pactamos contigo un plazo de entrega
 * distinto del legal, así que aplica el término de treinta (30) días calendario". Esa decisión —no
 * prometer un plazo propio más corto— está en docs/09-plan-de-arranque.md, y mientras siga en pie
 * este número es el único que hay que mantener sincronizado con el texto.
 *
 * <p>Pura, como {@link co.tecnosport.api.domain.retracto.PlazoDeRetracto} y {@link
 * PoliticaContraentrega}: no sabe de repositorios ni de configuración. A diferencia de aquél, <b>no
 * recibe un calendario</b>, y no es un olvido: este plazo cuenta días calendario, no hábiles, así
 * que ni los fines de semana ni los festivos lo empujan. De ahí que {@link #verdicto} nunca
 * devuelva {@link VerdictoPlazo#INDETERMINADO} — sin festivos de por medio no queda ninguna
 * incertidumbre que declarar.
 *
 * <p>La cuenta empieza el día <b>siguiente</b> —"a partir del día siguiente", dice el artículo— y
 * el plazo termina al acabar el trigésimo día, no a la hora exacta en que se confirmó el pedido: un
 * plazo en días se agota cuando el día se agota.
 */
public final class PlazoDeEntrega {

  /**
   * Público porque quien vigila los vencimientos necesita el mismo número para acotar su consulta:
   * un pedido creado hace menos de treinta días no puede haber incumplido nada, así que no hay por
   * qué traérselo de la base. Tenerlo en un solo sitio evita que esa consulta y esta cuenta se
   * desincronicen el día que el número cambie.
   */
  public static final int DIAS_CALENDARIO = 30;

  private PlazoDeEntrega() {}

  /** El último instante para entregar sin incumplir. */
  public static Instant limite(Instant inicio) {
    Objects.requireNonNull(inicio, "La fecha de inicio del plazo de entrega no puede ser nula.");
    LocalDate ultimoDia =
        inicio.atZone(ZonaDelNegocio.ZONA).toLocalDate().plusDays(DIAS_CALENDARIO);
    return ultimoDia.plusDays(1).atStartOfDay(ZonaDelNegocio.ZONA).toInstant();
  }

  /**
   * El veredicto, que es lo que el panel muestra y lo que decide si hay que avisarle al comprador.
   *
   * <p>Nunca bloquea nada ni cancela nada: el artículo 18 le da al <b>comprador</b> la opción de
   * terminar el contrato, no obliga al negocio a deshacerlo por su cuenta. Ver {@code ADR-0028}.
   */
  public static VerdictoPlazo verdicto(Instant inicio, Instant ahora) {
    Objects.requireNonNull(ahora, "El instante actual no puede ser nulo.");
    return ahora.isAfter(limite(inicio)) ? VerdictoPlazo.VENCIDO : VerdictoPlazo.EN_PLAZO;
  }
}
