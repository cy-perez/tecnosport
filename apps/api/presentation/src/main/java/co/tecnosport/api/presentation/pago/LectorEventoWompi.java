package co.tecnosport.api.presentation.pago;

import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;

/**
 * Lee el payload del webhook de Wompi. {@code signature.properties} declara, evento por evento, qué
 * campos están firmados — no hay un DTO fijo posible para eso, así que se navega el JSON crudo con
 * las rutas que el propio evento trae (docs/11-pagos-y-envios.md). Verificado contra un ejemplo
 * real de la documentación pública de eventos de Wompi.
 */
final class LectorEventoWompi {

  private LectorEventoWompi() {}

  static boolean esActualizacionDeTransaccion(JsonNode cuerpo) {
    return "transaction.updated".equals(cuerpo.path("event").asString());
  }

  static String referencia(JsonNode cuerpo) {
    return cuerpo.path("data").path("transaction").path("reference").asString();
  }

  static String estado(JsonNode cuerpo) {
    return cuerpo.path("data").path("transaction").path("status").asString();
  }

  /**
   * Con qué se cobró de verdad. Wompi no recibe el método que el comprador eligió en nuestro
   * checkout —el Web Checkout hospedado pinta su propia lista— así que este campo es la única
   * fuente de ese hecho. Cadena vacía si el evento no lo trae: se navega el JSON crudo y {@code
   * path} nunca revienta por un campo ausente.
   */
  static String medio(JsonNode cuerpo) {
    return cuerpo.path("data").path("transaction").path("payment_method_type").asString();
  }

  static long timestamp(JsonNode cuerpo) {
    return cuerpo.path("timestamp").asLong();
  }

  static String checksum(JsonNode cuerpo) {
    return cuerpo.path("signature").path("checksum").asString();
  }

  /**
   * Cada ruta en {@code signature.properties} (ej. {@code "transaction.id"}) es relativa a {@code
   * data}.
   */
  static List<String> valoresDePropiedadesFirmadas(JsonNode cuerpo) {
    JsonNode data = cuerpo.path("data");
    List<String> valores = new ArrayList<>();
    for (JsonNode propiedad : cuerpo.path("signature").path("properties")) {
      valores.add(resolverRuta(data, propiedad.asString()));
    }
    return valores;
  }

  private static String resolverRuta(JsonNode raiz, String rutaConPuntos) {
    JsonNode nodo = raiz;
    for (String segmento : rutaConPuntos.split("\\.")) {
      nodo = nodo.path(segmento);
    }
    return nodo.asString();
  }
}
