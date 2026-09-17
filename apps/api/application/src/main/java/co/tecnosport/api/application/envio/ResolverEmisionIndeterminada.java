package co.tecnosport.api.application.envio;

import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.domain.envio.AcuseDeRevision;
import co.tecnosport.api.domain.envio.EmisionDeGuia;
import java.time.Instant;
import java.util.Objects;

/**
 * Desenreda una emisión {@code INDETERMINADA} con lo que una persona vio en el panel de la
 * plataforma, y con eso desbloquea el pedido.
 *
 * <p>Era lo que faltaba para que la bandeja de revisión sirviera de algo en su mitad más cara:
 * acusar una indeterminada dejaba constancia y la sacaba de la vista, pero el pedido seguía sin
 * poder emitir otra vez —el índice único de emisiones abiertas se lo impide, y con razón: una
 * emisión nueva encima de una que pudo cobrar sería pagar dos veces—.
 *
 * <p><strong>El sistema no adivina, registra lo que alguien vio.</strong> Se consideró resolverlo
 * solo: reenviar {@code POST /shipments} con el mismo {@code idTarifa} y dejar que la caché de
 * idempotencia de la plataforma —96 horas por {@code rate_id}— devolviera el envío si existía. Se
 * descartó por dos motivos. El primero es que esa caché está <em>documentada</em> por el proveedor
 * y no medida por nosotros, y este proveedor ya cobró cuatro veces el precio de creerle a su
 * documentación (docs/13-skydropx-capacidades.md). El segundo es que fuera de esa ventana el mismo
 * reenvío crearía un segundo envío pagado, que es exactamente el desastre que todo este diseño
 * existe para evitar. Quien resuelve está mirando el panel: <em>ve</em> si el envío está, y eso no
 * necesita ninguna suposición.
 *
 * <p>Deja además un {@link AcuseDeRevision}, el mismo que escribe la bandeja: resolver es un caso
 * particular de "alguien miró esto y dice qué pasó", y separar los dos rastros haría que la fila
 * que cuenta quién decidió sobre plata comprometida viviera en dos sitios.
 */
public final class ResolverEmisionIndeterminada {

  private final RepositorioEmisiones emisiones;
  private final RepositorioAcusesDeRevision acuses;
  private final Reloj reloj;

  public ResolverEmisionIndeterminada(
      RepositorioEmisiones emisiones, RepositorioAcusesDeRevision acuses, Reloj reloj) {
    this.emisiones = Objects.requireNonNull(emisiones);
    this.acuses = Objects.requireNonNull(acuses);
    this.reloj = Objects.requireNonNull(reloj);
  }

  public EmisionDeGuia ejecutar(ResolverEmisionIndeterminadaComando comando) {
    Objects.requireNonNull(comando, "El comando no puede ser nulo.");
    EmisionDeGuia emision =
        emisiones
            .buscarPorId(comando.emisionId())
            .orElseThrow(() -> new EmisionNoEncontradaException(comando.emisionId()));

    Instant ahora = reloj.ahora();
    switch (comando.veredicto()) {
      case SIN_COBRO -> emision.descartadaSinCobro(detalleDe(comando), ahora);
      case CON_ENVIO -> {
        if (comando.enviosEnPlataforma().isEmpty()) {
          throw new AcuseNoAplicableException(
              "Decir que el envío está exige decir cuál: sin identificador no hay nada que releer.");
        }
        emision.recuperada(comando.enviosEnPlataforma(), ahora);
      }
    }

    emisiones.guardar(emision);
    acuses.guardar(AcuseDeRevision.deEmision(emision.id(), comando.actor(), comando.nota(), ahora));
    return emision;
  }

  /**
   * Por qué quedó fallida, con la nota de quien lo afirmó si la escribió. El texto fijo va delante
   * porque el estado por sí solo no distingue esta fallida de las que cerró la plataforma, y esa
   * diferencia importa: aquí la afirmación es de una persona.
   */
  private static String detalleDe(ResolverEmisionIndeterminadaComando comando) {
    String base = "Revisada en el panel de la plataforma: el envío no existe, no hubo cobro.";
    String nota = comando.nota();
    return nota == null || nota.isBlank() ? base : base + " " + nota.trim();
  }
}
