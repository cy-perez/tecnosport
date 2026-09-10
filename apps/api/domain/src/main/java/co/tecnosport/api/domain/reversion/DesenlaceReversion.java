package co.tecnosport.api.domain.reversion;

/**
 * Cómo terminó la solicitud de reversión.
 *
 * <p>Los dos primeros valores existen separados porque el dinero vuelve por caminos distintos y
 * solo uno de ellos deja constancia en este sistema: cuando revierte el emisor, la plata regresa
 * por la red de pagos y el comercio no mueve un peso; cuando el comercio decide devolverla
 * directamente, sí sale de aquí y tiene que quedar su {@code Reintegro}. Fundirlos en un solo
 * "resuelto" haría imposible responder cuánto devolvimos nosotros.
 */
public enum DesenlaceReversion {
  /** El emisor del medio de pago revirtió la transacción. El comercio no movió dinero. */
  REVERTIDO_POR_EL_EMISOR,
  /** El comercio devolvió el dinero por su cuenta, con su constancia. */
  REINTEGRADO_DIRECTAMENTE,
  /** El emisor o el comercio la rechazaron. */
  RECHAZADA,
  /** El comprador desistió. */
  DESISTIDA
}
