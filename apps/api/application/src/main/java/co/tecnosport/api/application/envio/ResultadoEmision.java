package co.tecnosport.api.application.envio;

import java.util.List;

/**
 * Qué contestó la plataforma al pedirle las guías.
 *
 * <p>Son solo dos casos, y la asimetría con {@link ResultadoCotizacion} —que tiene tres— es
 * deliberada: <strong>aceptada no quiere decir emitida</strong>. La plataforma responde {@code 202}
 * con {@code payment_status: paid} y la guía en {@code null}, y el desenlace llega minutos después
 * por otro camino. Lo único que se decide aquí es si hay algo que esperar.
 *
 * <p>Y por eso {@link Rechazada} es la respuesta <em>buena</em> cuando algo va mal: un rechazo no
 * cuesta saldo. Lo caro es el {@link Aceptada} que después muere.
 */
public sealed interface ResultadoEmision {

  /**
   * La plataforma creó los envíos y ya cobró. Varios cuando el pedido va en varios bultos: la
   * tarifa pasa a {@code multishipment} y crea un envío por bulto (adr/0031).
   */
  record Aceptada(List<String> enviosEnPlataforma) implements ResultadoEmision {

    public Aceptada {
      if (enviosEnPlataforma == null || enviosEnPlataforma.isEmpty()) {
        throw new IllegalArgumentException("Una emisión aceptada trae al menos un envío.");
      }
      enviosEnPlataforma = List.copyOf(enviosEnPlataforma);
    }
  }

  /** No se creó nada y no se cobró nada. {@code detalle} es para el registro y para el panel. */
  record Rechazada(Motivo motivo, String detalle) implements ResultadoEmision {

    public Rechazada {
      if (motivo == null) {
        throw new IllegalArgumentException("Un rechazo exige decir cuál fue.");
      }
    }
  }

  /**
   * Piden cosas distintas de quien opera, y por eso no se agrupan. Sin credenciales es un
   * despliegue mal configurado; datos rechazados es algo del pedido o de la tarifa; el proveedor no
   * disponible se reintenta; y una respuesta inesperada es la forma del proveedor cambiando bajo
   * nuestros pies.
   *
   * <p><strong>No hay un motivo para "la tarifa venció"</strong>, y no es un olvido: la plataforma
   * no lo dice así. Cuando la tarifa no resuelve, el {@code 422} no habla de la tarifa — enumera
   * los campos que el envío <em>habría heredado</em> de la cotización, todos "no puede estar en
   * blanco" (medido con un {@code rate_id} inexistente, docs/13-skydropx-capacidades.md §6.10).
   * Desde fuera es indistinguible de una dirección incompleta, así que inventar el motivo sería
   * adivinar; el cuerpo va entero en {@code detalle}, que es donde de verdad está la respuesta.
   */
  enum Motivo {
    SIN_CREDENCIALES,
    DATOS_RECHAZADOS,
    PROVEEDOR_NO_DISPONIBLE,
    RESPUESTA_INESPERADA
  }
}
