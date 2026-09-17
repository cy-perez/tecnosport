package co.tecnosport.api.application.envio;

/**
 * Qué contestó la plataforma al pedirle que anule una guía ya emitida.
 *
 * <p>Dos casos, y la asimetría con {@link ResultadoEmision} es la contraria de la que se esperaría:
 * al emitir, el rechazo es la respuesta buena porque no cuesta saldo; aquí <strong>el rechazo es la
 * respuesta cara</strong>, porque significa que queda una guía viva de un pedido que ya no existe.
 *
 * <p><strong>Un envío ya cancelado cuenta como {@link Cancelada}.</strong> La plataforma responde
 * {@code 422 "El envío no se puede cancelar"} tanto cuando ya se anuló como cuando la
 * transportadora ya lo recogió, y desde aquí no se distinguen; lo que sí se puede decir es que
 * insistir no cambia nada, y que pedir dos veces la cancelación de la misma guía no puede acabar en
 * dos avisos distintos. El {@code detalle} guarda lo que respondió, para quien tenga que mirarlo en
 * el panel.
 *
 * <p>No hay un tercer caso para "no se pudo preguntar", a diferencia de {@link
 * LecturaDeEnvioEmitido}. Ahí la distinción decidía si se cerraba una emisión a ciegas; aquí los
 * dos desenlaces posibles —la plataforma dijo que no, o no contestó— llevan exactamente al mismo
 * sitio: una persona mirando una guía que quizá siga viva. Separarlos daría dos nombres a la misma
 * acción.
 */
public sealed interface ResultadoCancelacion {

  /** La guía quedó anulada, o ya lo estaba. No hay nada más que hacer con ella. */
  record Cancelada() implements ResultadoCancelacion {}

  /**
   * Sigue viva, o no se sabe. En los dos casos hay un paquete que puede moverse y un pedido que
   * dice que no debería. {@code detalle} es para el registro y para el panel.
   */
  record NoSePudo(String detalle) implements ResultadoCancelacion {}
}
