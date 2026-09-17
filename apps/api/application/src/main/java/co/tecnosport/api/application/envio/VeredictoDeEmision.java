package co.tecnosport.api.application.envio;

/**
 * Qué vio la persona en el panel de la plataforma. No es una opinión ni una decisión: es lo que
 * había.
 *
 * <p>Son dos y no tres porque "no sé" no es un veredicto — es dejar la emisión como está, que es lo
 * que ya pasa si nadie hace nada. Para eso está el acuse, que deja constancia de que alguien miró
 * sin afirmar nada.
 */
public enum VeredictoDeEmision {

  /**
   * El envío no está en la plataforma: nunca se creó y no hubo cobro. La emisión queda fallida y el
   * pedido libre para intentarlo otra vez.
   */
  SIN_COBRO,

  /**
   * El envío sí está, y vienen sus identificadores. Es exactamente lo que la llamada perdió al
   * morirse en la mitad: la emisión vuelve a estar en curso y la relee la tarea de siempre.
   */
  CON_ENVIO
}
