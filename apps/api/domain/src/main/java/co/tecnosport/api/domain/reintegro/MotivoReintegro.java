package co.tecnosport.api.domain.reintegro;

/**
 * Por qué se devuelve el dinero. No es una etiqueta de reporte: cada motivo nace de una obligación
 * distinta, con su propio disparador y su propio plazo, y confundirlos es el error de fondo que
 * este enum existe para impedir.
 *
 * <p>Los cinco salen de leer los términos y condiciones publicados buscando toda frase que prometa
 * devolver dinero, no de la lista de figuras legales que uno espera encontrar. Los dos últimos
 * viven en secciones que nadie lee como secciones de dinero —"Disponibilidad", "Envío y entrega"— y
 * por eso llevaban tiempo sin que nadie los contara.
 */
public enum MotivoReintegro {

  /** El comprador se arrepintió, sin necesidad de justificar (Ley 1480 de 2011, art. 47). */
  RETRACTO,

  /** El producto falló y la salida elegida fue devolver el dinero, no reparar ni reponer. */
  GARANTIA,

  /**
   * Fraude, no entrega, producto distinto al pedido o producto defectuoso (art. 51). Tiene causales
   * tasadas e involucra al emisor del medio de pago; no es un retracto con otro nombre.
   */
  REVERSION,

  /** La existencia desapareció después de la compra y el comprador prefirió su dinero. */
  NO_DISPONIBILIDAD,

  /** No se entregó dentro del plazo pactado y el comprador terminó el contrato. */
  PLAZO_INCUMPLIDO
}
