package co.tecnosport.api.domain.pedido;

/**
 * Por qué el negocio cancela un pedido antes de despacharlo.
 *
 * <p>Los dos salen de leer los términos y condiciones publicados, no de la lista de figuras legales
 * que uno espera encontrar. Viven en secciones que nadie lee como secciones de dinero
 * —"Disponibilidad" y "Envío y entrega"— y por eso llevaban tiempo prometidos sin que existiera
 * ningún camino que los ejecutara.
 */
public enum MotivoCancelacion {

  /**
   * "Si un producto deja de estar disponible después de tu compra, te lo comunicaremos de inmediato
   * para que decidas si esperas una nueva fecha o prefieres la devolución de tu dinero."
   */
  NO_DISPONIBILIDAD,

  /**
   * "Si no entregamos dentro del plazo pactado, puedes terminar el contrato y recuperar tu dinero."
   */
  PLAZO_INCUMPLIDO
}
