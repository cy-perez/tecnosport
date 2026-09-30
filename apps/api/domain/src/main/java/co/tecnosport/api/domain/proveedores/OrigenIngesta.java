package co.tecnosport.api.domain.proveedores;

/**
 * Por dónde llegaron los mensajes de un lote.
 *
 * <p>Los dos existen desde el primer día aunque solo uno tenga adaptador: la exportación del chat
 * es el camino de esta iteración y la API de WhatsApp el de la siguiente. Que el lote sepa de cuál
 * vino es lo que permite mirar después qué proporción del catálogo entró por cada uno, y retirar la
 * exportación cuando ya no aporte nada.
 */
public enum OrigenIngesta {
  /** Un zip con el {@code .txt} y las fotos, exportado desde el celular y subido en el panel. */
  EXPORTACION_CHAT,

  /** El webhook de la WhatsApp Cloud API. Sin adaptador todavía. */
  CLOUD_API
}
