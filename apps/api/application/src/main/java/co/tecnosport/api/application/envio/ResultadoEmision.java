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
   * Piden cosas distintas de quien opera, y por eso no se agrupan. {@code TARIFA_RECHAZADA} es la
   * más probable en la práctica: las tarifas de Skydropx valen 24 horas y entre el pago y el
   * despacho suele pasar más, así que emitir <em>siempre</em> recotiza — si aun así la rechaza, es
   * que la cotización de hace un momento ya no sirve y hay que volver a empezar, no reintentar con
   * la misma.
   */
  enum Motivo {
    SIN_CREDENCIALES,
    TARIFA_RECHAZADA,
    DATOS_RECHAZADOS,
    PROVEEDOR_NO_DISPONIBLE,
    RESPUESTA_INESPERADA
  }
}
