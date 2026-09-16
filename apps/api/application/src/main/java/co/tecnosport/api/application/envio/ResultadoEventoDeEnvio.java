package co.tecnosport.api.application.envio;

/**
 * Qué pasó con un evento de seguimiento. Siete desenlaces, y ninguno es un error: el webhook
 * responde 200 en todos (adr/0022), porque reintentar no arregla ninguno.
 */
public enum ResultadoEventoDeEnvio {

  /** La firma no cuadra, o todavía no se puede verificar. Se descarta sin aplicar nada. */
  FIRMA_INVALIDA,

  /** La firma cuadró y el cuerpo no se supo leer. Se descarta y queda registrado en el log. */
  NO_SE_PUDO_LEER,

  /**
   * No hay envío con esa guía. Un evento de un despacho que no es nuestro, o llegado antes de que
   * el despacho se registrara. Se descarta.
   */
  GUIA_DESCONOCIDA,

  /**
   * Sin novedad: el rastreo no trajo ningún evento que no estuviera ya. Cubre los dos casos que
   * para quien llama son el mismo —no llegó ninguno, o llegaron y todos eran conocidos— y ninguno
   * mueve nada.
   */
  REPETIDO,

  /**
   * La guía es nuestra y no sabemos con qué código la conoce la plataforma, así que no se le puede
   * preguntar por ella (adr/0022). Pasa con las guías que teclea una persona en el panel, que
   * además pueden no existir en la plataforma. Se cuenta aparte a propósito: si se registrara como
   * "sin novedad", un despacho que nadie está vigilando se vería igual que uno conciliado.
   */
  SIN_CODIGO_DE_TRANSPORTADORA,

  /** Registrado en el rastro, sin mover el pedido. Es el caso de ocho de los doce estados. */
  REGISTRADO,

  /** Registrado y además movió el pedido: recogido, entregado o en devolución. */
  REGISTRADO_Y_APLICADO
}
