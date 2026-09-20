package co.tecnosport.api.presentation.pago;

import tools.jackson.databind.JsonNode;

/**
 * Lee los tres campos de la notificación de Sistecrédito que hay que contrastar contra la consulta
 * (guía {@code G-ALI-08}). El cuerpo llega como {@code JsonNode} y no como un DTO tipado por la
 * misma razón que el de Wompi: su forma la decide la pasarela, no nosotros, y una notificación con
 * un campo de más no puede tumbar la petición.
 *
 * <p>La notificación trae la misma estructura que la respuesta de la consulta, pero <b>sin</b> los
 * campos {@code function}, {@code errorCode} y {@code message} (guía {@code G-SCL-21} §3.3), y con
 * el nodo {@code extraData} que la consulta no expone. De eso solo interesan tres valores.
 */
final class LectorNotificacionSistecredito {

  private LectorNotificacionSistecredito() {}

  /**
   * La pasarela manda la notificación por POST con el cuerpo, o por GET con la misma información en
   * la query. Aquí solo se acepta POST —es lo que se declara en {@code methodConfirmation}— así que
   * el cuerpo puede venir con la transacción en la raíz o dentro de {@code data}, según si la
   * pasarela reenvía la respuesta completa o solo su contenido. Se miran los dos sitios: la guía
   * enseña las dos formas en páginas distintas.
   */
  static JsonNode transaccion(JsonNode cuerpo) {
    JsonNode datos = cuerpo.path("data");
    return datos.isObject() && datos.hasNonNull("_id") ? datos : cuerpo;
  }

  static String idTransaccion(JsonNode cuerpo) {
    return texto(transaccion(cuerpo).path("_id"));
  }

  static String referencia(JsonNode cuerpo) {
    return texto(transaccion(cuerpo).path("invoice"));
  }

  static String estado(JsonNode cuerpo) {
    return texto(transaccion(cuerpo).path("transactionStatus"));
  }

  private static String texto(JsonNode nodo) {
    if (nodo == null || nodo.isMissingNode() || nodo.isNull()) {
      return null;
    }
    String valor = nodo.asString();
    return valor == null || valor.isBlank() ? null : valor.trim();
  }
}
