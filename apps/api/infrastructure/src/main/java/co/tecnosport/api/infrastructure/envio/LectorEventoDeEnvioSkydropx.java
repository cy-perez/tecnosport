package co.tecnosport.api.infrastructure.envio;

import co.tecnosport.api.application.envio.LectorEventoDeEnvio;
import co.tecnosport.api.application.envio.LecturaDeEvento;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * El cuerpo del webhook de Skydropx, del que solo hace falta una cosa: de qué guía habla (adr/0032,
 * y el porqué está en {@code LectorEventoDeEnvio}).
 *
 * <p>La forma sale de la sección <em>Webhooks</em> de la documentación oficial, leída el 14 y
 * releída el 16 de septiembre de 2026, que trae ejemplos completos. Es JSON:API:
 *
 * <ul>
 *   <li><strong>{@code data.type} decide si el evento es nuestro.</strong> La documentación
 *       describe además {@code orders}, {@code quotation}, {@code rate}, {@code extra_charges} y
 *       {@code pickups}; solo {@code packages} habla de un paquete en movimiento. El panel de esta
 *       cuenta sólo deja suscribir los once de paquetes (docs/13 §6.9), así que hoy el filtro es
 *       una defensa y no un desvío que se use.
 *   <li><strong>{@code data.id} es el identificador del paquete, no del envío ni del
 *       evento.</strong> Con varias guías por envío eso significa un evento por guía, y por eso el
 *       amarre es por el número de guía y no por ese id.
 *   <li><strong>{@code attributes.tracking_number} es la guía.</strong>
 * </ul>
 *
 * <p>Lo que <em>no</em> se lee, y conviene saber por qué: {@code status} y {@code returned_status}
 * sí vienen, pero sin fecha ni identificador de evento no se puede construir un {@link
 * co.tecnosport.api.application.envio.AplicarEventoDeEnvioComando} honesto. Además el retorno tiene
 * una regla propia —durante todo el trayecto {@code status} se queda en {@code in_return} y el
 * movimiento real viaja en {@code returned_status}— que sería una segunda forma de leer lo mismo.
 * El rastreo es la única fuente del rastro.
 *
 * <p>Ninguna excepción sale de aquí: un cuerpo que no es JSON, o que lo es y no tiene esta forma,
 * es {@link LecturaDeEvento.Ilegible}. El endpoint responde 200 igual y lo deja escrito.
 *
 * <p><strong>Un evento de otro tipo no es ilegible</strong>, y se responde aparte: se entendió
 * perfectamente y no habla de un paquete — el porqué de la distinción está en {@link
 * LecturaDeEvento}. Un {@code packages} sin número de guía sí es ilegible: ese sí venía dirigido a
 * nosotros y llegó incompleto.
 */
@Component
final class LectorEventoDeEnvioSkydropx implements LectorEventoDeEnvio {

  private static final String TIPO_DE_PAQUETE = "packages";
  private static final LecturaDeEvento ILEGIBLE = new LecturaDeEvento.Ilegible();

  private final JsonMapper json = JsonMapper.builder().build();

  @Override
  public LecturaDeEvento leer(String cuerpoCrudo) {
    if (cuerpoCrudo == null || cuerpoCrudo.isBlank()) {
      return ILEGIBLE;
    }
    JsonNode raiz;
    try {
      raiz = json.readTree(cuerpoCrudo);
    } catch (RuntimeException e) {
      return ILEGIBLE;
    }

    JsonNode datos = raiz.path("data");
    String tipo = texto(datos.path("type"));
    if (tipo.isBlank()) {
      return ILEGIBLE;
    }
    if (!TIPO_DE_PAQUETE.equals(tipo)) {
      return new LecturaDeEvento.DeOtroTipo(tipo);
    }
    String guia = texto(datos.path("attributes").path("tracking_number"));
    return guia.isBlank() ? ILEGIBLE : new LecturaDeEvento.DeUnaGuia(guia);
  }

  private static String texto(JsonNode nodo) {
    if (nodo == null || nodo.isMissingNode() || nodo.isNull()) {
      return "";
    }
    String valor = nodo.asString();
    return valor == null ? "" : valor.trim();
  }
}
