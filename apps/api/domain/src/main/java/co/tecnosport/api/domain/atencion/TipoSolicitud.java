package co.tecnosport.api.domain.atencion;

/**
 * Qué clase de solicitud llegó al buzón de atención, que es lo que decide qué reloj corre.
 *
 * <p>Este enum existe por un hallazgo, no por gusto: <b>el mismo correo recibe solicitudes que la
 * ley cuenta con plazos distintos</b>. Una consulta de datos personales y un reclamo de datos
 * personales tienen los suyos (Ley 1581 de 2012, arts. 14 y 15, con prórrogas propias), y las
 * peticiones del consumidor tienen el que el propio sitio promete en sus términos. Meterlas todas
 * en una sola constante era la forma segura de incumplir la más corta sin que nadie lo notara.
 *
 * <p>{@code GARANTIA} y {@code REVERSION} entran aquí y no en un módulo aparte porque llegan por el
 * mismo canal que el resto —el correo y el WhatsApp que anuncian los términos— y lo que cambia
 * entre ellas y una queja es el desenlace, no la radicación.
 */
public enum TipoSolicitud {

  /** Petición general del consumidor. */
  PETICION,

  QUEJA,

  /** Reclamo del consumidor sobre un producto o una compra. */
  RECLAMO,

  /** El titular pregunta por sus datos personales (Ley 1581 de 2012, art. 14). */
  CONSULTA_DATOS,

  /**
   * El titular reclama sobre el tratamiento de sus datos: corregir, actualizar, suprimir o revocar
   * la autorización (art. 15). Plazo distinto del de la consulta, y más largo.
   */
  RECLAMO_DATOS,

  /** El producto falló y se pide reparación, reposición o devolución del dinero. */
  GARANTIA,

  /** Reversión del pago por alguna de las causales tasadas (Ley 1480 de 2011, art. 51). */
  REVERSION
}
