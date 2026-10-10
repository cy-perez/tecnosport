package co.tecnosport.api.infrastructure.proveedores.extraccion;

import co.tecnosport.api.application.proveedores.ExtraccionFallidaException;
import co.tecnosport.api.application.proveedores.UsoDelExtractor;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Una petición a {@code /v1/messages} con salida estructurada, con sus reintentos: lo que comparten
 * el extractor de productos y el lector de fotos. Lo que cada uno manda y cómo lee el JSON es suyo.
 *
 * <p>Un 429 y un 5xx se reintentan con espera exponencial: son la API ocupada, no la petición mal
 * hecha. Un 4xx distinto no se reintenta y sale como fallo con el código. Una respuesta con {@code
 * stop_reason} de {@code refusal} o de {@code max_tokens} tampoco: el modelo no va a contestar
 * distinto la segunda vez. Al registro van modelo, tokens y latencia; nunca el contenido.
 */
final class LlamadaAClaude {

  private static final Logger log = LoggerFactory.getLogger(LlamadaAClaude.class);

  /** La versión de la API que fija la forma de la respuesta. Documentada; no cambia sola. */
  static final String VERSION_DE_LA_API = "2023-06-01";

  interface Pausador {
    void pausar(Duration duracion) throws InterruptedException;
  }

  /**
   * @param jsonCrudo el texto del primer bloque, que con salida estructurada es el JSON
   */
  record Respuesta(String jsonCrudo, UsoDelExtractor uso) {}

  private final HttpClient httpClient;
  private final JsonMapper json = JsonMapper.builder().build();
  private final URI urlBase;
  private final String apiKey;
  private final String modelo;
  private final Duration timeout;
  private final int intentos;
  private final Duration esperaInicial;
  private final Pausador pausador;
  private final String quien;

  /**
   * @param quien el nombre con que los mensajes de error y el registro nombran al que llama: «El
   *     extractor», «El lector de fotos»
   */
  LlamadaAClaude(
      URI urlBase,
      String apiKey,
      String modelo,
      Duration timeout,
      int intentos,
      Duration esperaInicial,
      Pausador pausador,
      String quien) {
    this.urlBase = Objects.requireNonNull(urlBase, "La URL base de la API es obligatoria.");
    this.apiKey = exigir(apiKey, "La clave de la API es obligatoria.");
    this.modelo = exigir(modelo, "El modelo es obligatorio.");
    if (intentos <= 0) {
      throw new IllegalArgumentException("Los intentos tienen que ser positivos.");
    }
    this.timeout = Objects.requireNonNull(timeout, "El timeout es obligatorio.");
    this.intentos = intentos;
    this.esperaInicial = Objects.requireNonNull(esperaInicial, "La espera inicial lo es.");
    this.pausador = Objects.requireNonNull(pausador);
    this.quien = exigir(quien, "Quien llama tiene nombre.");
    this.httpClient = HttpClient.newBuilder().connectTimeout(timeout).build();
  }

  String modelo() {
    return modelo;
  }

  /**
   * @param cuerpo la petición entera, ya en JSON
   * @param queLee lo que se negó a leer, para el mensaje: «el mensaje», «las fotos»
   */
  Respuesta enviar(String cuerpo, String queLee) {
    long inicio = System.nanoTime();
    HttpResponse<String> respuesta = enviarConReintentos(cuerpo);
    long latenciaMilis = (System.nanoTime() - inicio) / 1_000_000;

    JsonNode raiz = json.readTree(respuesta.body());
    String stopReason = raiz.path("stop_reason").asString();
    if ("refusal".equals(stopReason)) {
      throw new ExtraccionFallidaException(quien + " se negó a leer " + queLee + ".");
    }
    if ("max_tokens".equals(stopReason)) {
      throw new ExtraccionFallidaException(
          quien + " cortó su respuesta por longitud; hay que subir max-tokens.");
    }
    JsonNode contenido = raiz.path("content");
    if (!contenido.isArray() || contenido.isEmpty()) {
      throw new ExtraccionFallidaException(quien + " respondió sin contenido.");
    }
    String jsonCrudo = contenido.get(0).path("text").asString();
    if (jsonCrudo == null || jsonCrudo.isBlank()) {
      throw new ExtraccionFallidaException(quien + " respondió con un bloque vacío.");
    }
    UsoDelExtractor uso =
        new UsoDelExtractor(
            raiz.path("model").asString(modelo),
            raiz.path("usage").path("input_tokens").asLong(0),
            raiz.path("usage").path("output_tokens").asLong(0),
            latenciaMilis);
    log.info(
        "{} con {}: {} tokens de entrada, {} de salida, {} ms",
        quien,
        uso.modelo(),
        uso.tokensDeEntrada(),
        uso.tokensDeSalida(),
        uso.latenciaMilis());
    return new Respuesta(jsonCrudo, uso);
  }

  private HttpResponse<String> enviarConReintentos(String cuerpo) {
    String ultimoMotivo = "sin respuesta";
    for (int intento = 0; intento < intentos; intento++) {
      if (intento > 0) {
        try {
          pausador.pausar(esperaInicial.multipliedBy(1L << (intento - 1)));
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new ExtraccionFallidaException(quien + " se interrumpió.", e);
        }
      }
      HttpResponse<String> respuesta;
      try {
        respuesta =
            httpClient.send(
                HttpRequest.newBuilder()
                    .uri(urlBase.resolve("/v1/messages"))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", VERSION_DE_LA_API)
                    .POST(HttpRequest.BodyPublishers.ofString(cuerpo, StandardCharsets.UTF_8))
                    .build(),
                HttpResponse.BodyHandlers.ofString());
      } catch (IOException e) {
        ultimoMotivo = "no se pudo hablar con la API: " + e.getMessage();
        log.warn("{}, intento {}: {}", quien, intento + 1, ultimoMotivo);
        continue;
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new ExtraccionFallidaException(quien + " se interrumpió.", e);
      }
      int estado = respuesta.statusCode();
      if (estado / 100 == 2) {
        return respuesta;
      }
      if (estado == 429 || estado / 100 == 5) {
        ultimoMotivo = "la API respondió " + estado;
        log.warn("{}, intento {}: {}", quien, intento + 1, ultimoMotivo);
        continue;
      }
      // Un 4xx distinto no mejora repitiéndolo. El mensaje de error de la API sí ayuda a
      // arreglarlo, y no lleva nada del proveedor.
      throw new ExtraccionFallidaException(
          "La API respondió " + estado + ": " + motivoDe(respuesta.body()));
    }
    throw new ExtraccionFallidaException(
        "La API no respondió tras " + intentos + " intentos (" + ultimoMotivo + ").");
  }

  private String motivoDe(String cuerpo) {
    try {
      String mensaje = json.readTree(cuerpo).path("error").path("message").asString();
      return mensaje == null || mensaje.isBlank() ? "sin detalle" : mensaje;
    } catch (RuntimeException e) {
      return "sin detalle";
    }
  }

  static String exigir(String valor, String mensaje) {
    if (valor == null || valor.isBlank()) {
      throw new IllegalArgumentException(mensaje);
    }
    return valor;
  }
}
