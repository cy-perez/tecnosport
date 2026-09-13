package co.tecnosport.api.application.envio;

/**
 * Qué pasó con un evento de seguimiento. Seis desenlaces, y ninguno es un error: el webhook
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

  /** Ya estaba registrado: el mismo identificador externo. Un reintento, y no se aplica nada. */
  REPETIDO,

  /** Registrado en el rastro, sin mover el pedido. Es el caso de ocho de los doce estados. */
  REGISTRADO,

  /** Registrado y además movió el pedido: recogido, entregado o en devolución. */
  REGISTRADO_Y_APLICADO
}
