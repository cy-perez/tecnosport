package co.tecnosport.api.infrastructure.proveedores.extraccion;

import co.tecnosport.api.application.proveedores.ExtraccionFallidaException;
import co.tecnosport.api.application.proveedores.ExtractorDeProductos;
import co.tecnosport.api.application.proveedores.ResultadoExtraccion;
import co.tecnosport.api.application.proveedores.TextoDePublicacion;
import co.tecnosport.api.application.proveedores.UsoDelExtractor;
import co.tecnosport.api.domain.proveedores.ProductoExtraido;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * El extractor contra la API de Claude, con salida estructurada.
 *
 * <p>La forma de la petición es la documentada en
 * platform.claude.com/docs/en/build-with-claude/structured-outputs (verificada el 30 de septiembre
 * de 2026): {@code output_config.format} de tipo {@code json_schema} con el esquema dentro, sin
 * cabecera beta, y el JSON llega en {@code content[0].text}. El prompt de sistema y el esquema no
 * son cadenas del código: viven en {@code ia/extractor-productos/} y se editan sin recompilar nada
 * más que el jar.
 *
 * <h2>Lo que se reintenta y lo que no</h2>
 *
 * <p>Un 429 y un 5xx se reintentan con espera exponencial: son la API ocupada, no la petición mal
 * hecha. Un 4xx distinto no se reintenta —repetir una petición inválida la vuelve a hacer inválida—
 * y sale como fallo con el código. Una respuesta con {@code stop_reason} de {@code refusal} o de
 * {@code max_tokens} tampoco: el modelo no va a contestar distinto la segunda vez.
 *
 * <h2>Lo que va al registro</h2>
 *
 * <p>Modelo, tokens y latencia por llamada, en {@code info}. Nunca el texto del mensaje ni la
 * respuesta: llevan lo que un tercero escribió, y el registro no es sitio para eso.
 */
public final class ExtractorClaude implements ExtractorDeProductos {

  private static final Logger log = LoggerFactory.getLogger(ExtractorClaude.class);

  /** La versión de la API que fija la forma de la respuesta. Documentada; no cambia sola. */
  static final String VERSION_DE_LA_API = "2023-06-01";

  interface Pausador {
    void pausar(Duration duracion) throws InterruptedException;
  }

  private final HttpClient httpClient;
  private final JsonMapper json = JsonMapper.builder().build();
  private final MapeadorDeExtraccion mapeador = new MapeadorDeExtraccion();
  private final URI urlBase;
  private final String apiKey;
  private final String modelo;
  private final int maxTokens;
  private final Duration timeout;
  private final int intentos;
  private final Duration esperaInicial;
  private final Pausador pausador;
  private final String promptDeSistema;
  private final JsonNode esquema;

  public ExtractorClaude(
      URI urlBase,
      String apiKey,
      String modelo,
      int maxTokens,
      Duration timeout,
      int intentos,
      Duration esperaInicial,
      String promptDeSistema,
      String esquemaJson) {
    this(
        urlBase,
        apiKey,
        modelo,
        maxTokens,
        timeout,
        intentos,
        esperaInicial,
        Thread::sleep,
        promptDeSistema,
        esquemaJson);
  }

  ExtractorClaude(
      URI urlBase,
      String apiKey,
      String modelo,
      int maxTokens,
      Duration timeout,
      int intentos,
      Duration esperaInicial,
      Pausador pausador,
      String promptDeSistema,
      String esquemaJson) {
    this.urlBase = Objects.requireNonNull(urlBase, "La URL base de la API es obligatoria.");
    this.apiKey = exigir(apiKey, "La clave de la API es obligatoria.");
    this.modelo = exigir(modelo, "El modelo es obligatorio.");
    if (maxTokens <= 0 || intentos <= 0) {
      throw new IllegalArgumentException("max-tokens e intentos tienen que ser positivos.");
    }
    this.maxTokens = maxTokens;
    this.timeout = Objects.requireNonNull(timeout, "El timeout es obligatorio.");
    this.intentos = intentos;
    this.esperaInicial = Objects.requireNonNull(esperaInicial, "La espera inicial lo es.");
    this.pausador = Objects.requireNonNull(pausador);
    this.promptDeSistema = exigir(promptDeSistema, "El prompt de sistema es obligatorio.");
    this.esquema = json.readTree(exigir(esquemaJson, "El esquema es obligatorio."));
    this.httpClient = HttpClient.newBuilder().connectTimeout(timeout).build();
  }

  @Override
  public ResultadoExtraccion extraer(TextoDePublicacion texto) {
    String cuerpo = cuerpoDe(texto);
    long inicio = System.nanoTime();
    HttpResponse<String> respuesta = enviarConReintentos(cuerpo);
    long latenciaMilis = (System.nanoTime() - inicio) / 1_000_000;

    JsonNode raiz = json.readTree(respuesta.body());
    String stopReason = raiz.path("stop_reason").asString();
    if ("refusal".equals(stopReason)) {
      throw new ExtraccionFallidaException("El extractor se negó a leer el mensaje.");
    }
    if ("max_tokens".equals(stopReason)) {
      throw new ExtraccionFallidaException(
          "La respuesta del extractor se cortó por longitud; hay que subir max-tokens.");
    }
    JsonNode contenido = raiz.path("content");
    if (!contenido.isArray() || contenido.isEmpty()) {
      throw new ExtraccionFallidaException("El extractor respondió sin contenido.");
    }
    String jsonCrudo = contenido.get(0).path("text").asString();
    if (jsonCrudo == null || jsonCrudo.isBlank()) {
      throw new ExtraccionFallidaException("El extractor respondió con un bloque vacío.");
    }
    List<ProductoExtraido> productos = mapeador.aProductos(jsonCrudo);

    UsoDelExtractor uso =
        new UsoDelExtractor(
            raiz.path("model").asString(modelo),
            raiz.path("usage").path("input_tokens").asLong(0),
            raiz.path("usage").path("output_tokens").asLong(0),
            latenciaMilis);
    log.info(
        "Extracción con {}: {} tokens de entrada, {} de salida, {} ms",
        uso.modelo(),
        uso.tokensDeEntrada(),
        uso.tokensDeSalida(),
        uso.latenciaMilis());
    return new ResultadoExtraccion(productos, jsonCrudo, uso);
  }

  private String cuerpoDe(TextoDePublicacion texto) {
    ObjectNode cuerpo = json.createObjectNode();
    cuerpo.put("model", modelo);
    cuerpo.put("max_tokens", maxTokens);
    cuerpo.put("system", promptDeSistema);
    ObjectNode mensaje = cuerpo.putArray("messages").addObject();
    mensaje.put("role", "user");
    mensaje.put(
        "content",
        "Línea del proveedor: "
            + texto.lineaDelProveedor().name().toLowerCase()
            + "\n\nMensaje:\n"
            + texto.completo());
    ObjectNode formato = cuerpo.putObject("output_config").putObject("format");
    formato.put("type", "json_schema");
    formato.set("schema", esquema);
    return json.writeValueAsString(cuerpo);
  }

  private HttpResponse<String> enviarConReintentos(String cuerpo) {
    String ultimoMotivo = "sin respuesta";
    for (int intento = 0; intento < intentos; intento++) {
      if (intento > 0) {
        try {
          pausador.pausar(esperaInicial.multipliedBy(1L << (intento - 1)));
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new ExtraccionFallidaException("La extracción se interrumpió.", e);
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
        log.warn("Extracción, intento {}: {}", intento + 1, ultimoMotivo);
        continue;
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new ExtraccionFallidaException("La extracción se interrumpió.", e);
      }
      int estado = respuesta.statusCode();
      if (estado / 100 == 2) {
        return respuesta;
      }
      if (estado == 429 || estado / 100 == 5) {
        ultimoMotivo = "la API respondió " + estado;
        log.warn("Extracción, intento {}: {}", intento + 1, ultimoMotivo);
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

  private static String exigir(String valor, String mensaje) {
    if (valor == null || valor.isBlank()) {
      throw new IllegalArgumentException(mensaje);
    }
    return valor;
  }
}
