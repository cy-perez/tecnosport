package co.tecnosport.api.application.envio;

import co.tecnosport.api.domain.envio.GuiaEnvio;
import java.util.List;
import java.util.Objects;

/**
 * Le pregunta a la plataforma qué sabe de una guía y aplica lo que encuentre. Es el trozo que
 * comparten los <strong>dos</strong> caminos del seguimiento (adr/0022): la tarea programada, que
 * lo hace con las guías calladas, y el webhook, que lo hace con la guía de la que acaba de avisar.
 *
 * <p><strong>Por qué el webhook también pasa por aquí, y no lee el evento de su propio
 * cuerpo</strong> (adr/0032): el cuerpo del webhook no trae ni identificador de evento ni fecha
 * —está medido y documentado, trae {@code status}, {@code tracking_number} y poco más—, y esos dos
 * datos no se pueden inventar: el identificador es lo que hace idempotente el rastro, y de la fecha
 * cuelgan plazos legales. Si cada camino se fabricara su propia llave, el mismo movimiento entraría
 * dos veces y el comprador leería "Entregado" dos veces. Con el rastreo como única fuente, los dos
 * caminos escriben exactamente el mismo evento y el webhook queda como lo que de verdad es: un
 * aviso de que vale la pena preguntar ya, en vez de esperar a la próxima vuelta.
 *
 * <p>El precio es una llamada más al proveedor por evento recibido, contra un límite de dos por
 * segundo. A este volumen no se nota, y el proveedor caído ya estaba cubierto: la tarea programada
 * existe justamente porque el camino rápido falla.
 */
public final class ConciliarGuia {

  private final ConsultorDeSeguimiento consultor;
  private final AplicarEventoDeEnvio aplicar;

  public ConciliarGuia(ConsultorDeSeguimiento consultor, AplicarEventoDeEnvio aplicar) {
    this.consultor = Objects.requireNonNull(consultor);
    this.aplicar = Objects.requireNonNull(aplicar);
  }

  /**
   * Devuelve el desenlace más fuerte de todos los eventos que trajo el rastreo, porque una consulta
   * trae la historia entera del paquete y no un movimiento suelto: si alguno movió el pedido, eso
   * es lo que pasó; si alguno se registró, eso; y si ninguno era nuevo, no hubo novedad.
   */
  public ResultadoEventoDeEnvio ejecutar(GuiaEnvio guia) {
    Objects.requireNonNull(guia, "La guía no puede ser nula.");
    if (!guia.conciliable()) {
      return ResultadoEventoDeEnvio.SIN_CODIGO_DE_TRANSPORTADORA;
    }

    List<AplicarEventoDeEnvioComando> eventos =
        consultor.consultar(guia.codigoTransportadora().orElseThrow(), guia.numero());

    ResultadoEventoDeEnvio desenlace = ResultadoEventoDeEnvio.REPETIDO;
    for (AplicarEventoDeEnvioComando evento : eventos) {
      ResultadoEventoDeEnvio resultado = aplicar.ejecutar(evento);
      if (resultado == ResultadoEventoDeEnvio.REGISTRADO_Y_APLICADO) {
        desenlace = resultado;
      } else if (resultado == ResultadoEventoDeEnvio.REGISTRADO
          && desenlace != ResultadoEventoDeEnvio.REGISTRADO_Y_APLICADO) {
        desenlace = resultado;
      }
    }
    return desenlace;
  }
}
