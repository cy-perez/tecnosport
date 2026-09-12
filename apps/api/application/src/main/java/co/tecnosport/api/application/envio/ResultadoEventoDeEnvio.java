package co.tecnosport.api.application.envio;

/**
 * Qué pasó con un evento de seguimiento. Cuatro desenlaces, y ninguno es un error: el webhook
 * responde 200 en todos (adr/0022), porque reintentar no arregla ninguno.
 */
public enum ResultadoEventoDeEnvio {

  /**
   * No hay envío con esa guía. Un evento de un despacho que no es nuestro, o llegado antes de que
   * el despacho se registrara. Se descarta.
   */
  GUIA_DESCONOCIDA,

  /** Ya estaba registrado: el mismo identificador externo. Un reintento, y no se aplica nada. */
  REPETIDO,

  /** Registrado en el rastro, sin mover el pedido. Es el caso de ocho de los doce estados. */
  REGISTRADO,

  /** Registrado y además movió el pedido: recogido, entregado o en devolución. */
  REGISTRADO_Y_APLICADO
}
