package co.tecnosport.api.infrastructure.proveedores.extraccion;

import co.tecnosport.api.application.proveedores.ExtractorDeProductos;
import co.tecnosport.api.application.proveedores.ResultadoExtraccion;
import co.tecnosport.api.application.proveedores.TextoDePublicacion;
import co.tecnosport.api.domain.proveedores.ProductoExtraido;
import java.net.URI;
import java.time.Duration;
import java.util.List;
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
 * <p>Lo que se reintenta y lo que va al registro lo decide {@link LlamadaAClaude}, que comparte con
 * el lector de fotos.
 */
public final class ExtractorClaude implements ExtractorDeProductos {

  interface Pausador extends LlamadaAClaude.Pausador {}

  private final JsonMapper json = JsonMapper.builder().build();
  private final MapeadorDeExtraccion mapeador = new MapeadorDeExtraccion();
  private final LlamadaAClaude llamada;
  private final int maxTokens;
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
    this.llamada =
        new LlamadaAClaude(
            urlBase, apiKey, modelo, timeout, intentos, esperaInicial, pausador, "El extractor");
    if (maxTokens <= 0) {
      throw new IllegalArgumentException("max-tokens tiene que ser positivo.");
    }
    this.maxTokens = maxTokens;
    this.promptDeSistema =
        LlamadaAClaude.exigir(promptDeSistema, "El prompt de sistema es obligatorio.");
    this.esquema = json.readTree(LlamadaAClaude.exigir(esquemaJson, "El esquema es obligatorio."));
  }

  @Override
  public ResultadoExtraccion extraer(TextoDePublicacion texto) {
    LlamadaAClaude.Respuesta respuesta = llamada.enviar(cuerpoDe(texto), "el mensaje");
    List<ProductoExtraido> productos = mapeador.aProductos(respuesta.jsonCrudo());
    return new ResultadoExtraccion(productos, respuesta.jsonCrudo(), respuesta.uso());
  }

  private String cuerpoDe(TextoDePublicacion texto) {
    ObjectNode cuerpo = json.createObjectNode();
    cuerpo.put("model", llamada.modelo());
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
}
