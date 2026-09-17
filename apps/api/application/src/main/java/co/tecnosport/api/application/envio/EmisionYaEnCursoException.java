package co.tecnosport.api.application.envio;

import co.tecnosport.api.domain.envio.EstadoEmision;
import java.util.UUID;

/**
 * Ya hay una emisión abierta para este pedido, así que la segunda no sale.
 *
 * <p>Es la puerta que más importa, porque la plataforma <strong>cobra al crear</strong> y no al
 * entregar la guía: dos solicitudes son dos cobros por el mismo pedido, y el segundo juego de guías
 * no lo va a usar nadie. La idempotencia por {@code rate_id} de Skydropx tampoco cubre esto de
 * forma fiable — cada intento recotiza, y basta con que el contenido cambie un poco para que la
 * tarifa sea otra y el cobro, nuevo.
 *
 * <p>La lanza el repositorio cuando la base rechaza la fila, no una lectura previa: entre leer y
 * escribir cabe un segundo clic. Que salga tipada de {@code infrastructure} es lo que impide que
 * una violación de unicidad de JPA termine en un 500 genérico.
 */
public class EmisionYaEnCursoException extends RuntimeException {

  private final UUID pedidoId;
  private final UUID emisionId;
  private final transient EstadoEmision estado;

  public EmisionYaEnCursoException(UUID pedidoId, UUID emisionId, EstadoEmision estado) {
    super(mensaje(pedidoId, emisionId, estado));
    this.pedidoId = pedidoId;
    this.emisionId = emisionId;
    this.estado = estado;
  }

  /**
   * La carrera: la lectura previa no vio ninguna abierta y la base sí. No se puede decir cuál ganó
   * —volver a consultar con la sesión ya rota por el choque no funciona—, y tampoco hace falta: lo
   * que importa es que esta no entró, o sea que no se cobró dos veces.
   */
  public EmisionYaEnCursoException(UUID pedidoId) {
    super(
        "Otra emisión del pedido "
            + pedidoId
            + " se registró primero; esta no se creó. Recarga el panel para ver en qué quedó.");
    this.pedidoId = pedidoId;
    this.emisionId = null;
    this.estado = null;
  }

  /**
   * El mensaje distingue el caso que pide una persona. Una emisión en curso se resuelve sola en
   * minutos y lo único que hay que hacer es esperar; una indeterminada no se resuelve sola nunca,
   * porque puede haber un envío pagado del que no tenemos identificador, y quien lea esto tiene que
   * saber que le toca a él.
   */
  private static String mensaje(UUID pedidoId, UUID emisionId, EstadoEmision estado) {
    if (estado == EstadoEmision.INDETERMINADA) {
      return "El pedido "
          + pedidoId
          + " tiene la emisión "
          + emisionId
          + " sin desenlace conocido: pudo haberse creado y cobrado una guía. Hay que revisarlo en"
          + " el panel de la transportadora antes de volver a emitir; mientras tanto, se puede"
          + " despachar registrando una guía emitida por fuera.";
    }
    return "El pedido "
        + pedidoId
        + " ya tiene la emisión "
        + emisionId
        + " en curso; hay que esperar a que la plataforma responda.";
  }

  public UUID pedidoId() {
    return pedidoId;
  }

  public UUID emisionId() {
    return emisionId;
  }

  public EstadoEmision estado() {
    return estado;
  }
}
