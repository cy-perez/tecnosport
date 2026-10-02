package co.tecnosport.api.infrastructure.proveedores.extraccion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.tecnosport.api.application.proveedores.ExtraccionFallidaException;
import co.tecnosport.api.application.proveedores.ResultadoExtraccion;
import co.tecnosport.api.application.proveedores.TextoDePublicacion;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.proveedores.TipoDeTalla;
import co.tecnosport.api.domain.proveedores.TipoProductoProveedor;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Contra un servidor HTTP de prueba del JDK. Lo que se comprueba es la forma de la petición —la
 * documentada para salida estructurada— y qué se reintenta y qué no. Que el esquema lo acepte la
 * API de verdad lo comprueba {@link ExtractorClaudeContratoTest}, que no corre en el pipeline.
 */
class ExtractorClaudeTest {

  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final String BOLSO =
      "{\"esta_agotado\":false,\"titulo\":\"Bolso de dama mediano\","
          + "\"linea\":\"Bolsos\",\"tipo\":\"bolso\",\"precio_proveedor_cop\":53000,"
          + "\"tallas\":{\"tipo\":\"desconocida\",\"sirve_hasta\":null,\"valores\":[]},"
          + "\"cantidad_tonos\":4,\"tonos_nombrados\":[],\"material\":\"importado\","
          + "\"caracteristicas\":[\"2 compartimientos internos\",\"incluye llavero\"],"
          + "\"confianza\":0.92,\"notas\":null}";
  private static final String PRODUCTO_JSON = "{\"productos\":[" + BOLSO + "]}";

  private HttpServer servidor;
  private final Deque<Respuesta> respuestas = new ArrayDeque<>();
  private final List<JsonNode> peticiones = new ArrayList<>();
  private final List<Map<String, String>> cabeceras = new ArrayList<>();
  private final List<Duration> pausas = new ArrayList<>();

  private record Respuesta(int estado, String cuerpo) {}

  @BeforeEach
  void arrancarServidor() throws IOException {
    servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    servidor.createContext("/v1/messages", this::atender);
    servidor.start();
  }

  @AfterEach
  void detenerServidor() {
    servidor.stop(0);
  }

  private void atender(HttpExchange intercambio) throws IOException {
    peticiones.add(JSON.readTree(intercambio.getRequestBody().readAllBytes()));
    cabeceras.add(
        Map.of(
            "x-api-key", intercambio.getRequestHeaders().getFirst("x-api-key"),
            "anthropic-version", intercambio.getRequestHeaders().getFirst("anthropic-version")));
    Respuesta respuesta = respuestas.isEmpty() ? new Respuesta(500, "{}") : respuestas.poll();
    byte[] cuerpo = respuesta.cuerpo().getBytes(StandardCharsets.UTF_8);
    intercambio.getResponseHeaders().add("Content-Type", "application/json");
    intercambio.sendResponseHeaders(respuesta.estado(), cuerpo.length);
    try (OutputStream salida = intercambio.getResponseBody()) {
      salida.write(cuerpo);
    }
  }

  private static String exito(String texto, String stopReason) {
    return "{\"id\":\"msg_1\",\"model\":\"claude-haiku-4-5-20251001\",\"stop_reason\":\""
        + stopReason
        + "\",\"content\":[{\"type\":\"text\",\"text\":"
        + JSON.writeValueAsString(texto)
        + "}],\"usage\":{\"input_tokens\":812,\"output_tokens\":143}}";
  }

  private ExtractorClaude extractor(int intentos) {
    return new ExtractorClaude(
        URI.create("http://127.0.0.1:" + servidor.getAddress().getPort()),
        "sk-prueba",
        "claude-haiku-4-5-20251001",
        1024,
        Duration.ofSeconds(5),
        intentos,
        Duration.ofSeconds(1),
        pausas::add,
        RecursosDelExtractor.promptDeSistema(),
        RecursosDelExtractor.esquema());
  }

  private static TextoDePublicacion texto() {
    return new TextoDePublicacion(
        "Bolso de dama mediano 👜\n💰 *53.000*",
        List.of("Este viene con la tira en cuero"),
        LineaCatalogo.BOLSOS);
  }

  @Test
  void mandaLaFormaDocumentadaDeSalidaEstructuradaYLeeElJsonDelBloqueDeTexto() {
    respuestas.add(new Respuesta(200, exito(PRODUCTO_JSON, "end_turn")));

    ResultadoExtraccion resultado = extractor(3).extraer(texto());

    JsonNode peticion = peticiones.get(0);
    assertThat(peticion.path("model").asString()).isEqualTo("claude-haiku-4-5-20251001");
    assertThat(peticion.path("max_tokens").asInt()).isEqualTo(1024);
    assertThat(peticion.path("system").asString()).contains("Nunca inventes");
    assertThat(peticion.path("output_config").path("format").path("type").asString())
        .isEqualTo("json_schema");
    assertThat(
            peticion
                .path("output_config")
                .path("format")
                .path("schema")
                .path("additionalProperties")
                .asBoolean(true))
        .isFalse();
    assertThat(peticion.path("messages").get(0).path("content").asString())
        .contains("Línea del proveedor: bolsos")
        .contains("💰 *53.000*")
        .contains("Este viene con la tira en cuero");
    assertThat(cabeceras.get(0))
        .containsEntry("x-api-key", "sk-prueba")
        .containsEntry("anthropic-version", "2023-06-01");

    assertThat(resultado.productos().getFirst().titulo()).isEqualTo("Bolso de dama mediano");
    assertThat(resultado.productos().getFirst().linea()).isEqualTo(LineaCatalogo.BOLSOS);
    assertThat(resultado.productos().getFirst().tipo()).isEqualTo(TipoProductoProveedor.BOLSO);
    assertThat(resultado.productos().getFirst().precioProveedor()).isEqualTo(Dinero.deCop(53000));
    assertThat(resultado.productos().getFirst().cantidadTonos()).isEqualTo(4);
    assertThat(resultado.productos().getFirst().tallas().tipo()).isEqualTo(TipoDeTalla.DESCONOCIDA);
    assertThat(resultado.jsonCrudo()).isEqualTo(PRODUCTO_JSON);
    assertThat(resultado.uso().modelo()).isEqualTo("claude-haiku-4-5-20251001");
    assertThat(resultado.uso().tokensDeEntrada()).isEqualTo(812);
    assertThat(resultado.uso().tokensDeSalida()).isEqualTo(143);
  }

  @Test
  void un429YUn5xxSeReintentanConEsperaExponencial() {
    respuestas.add(new Respuesta(429, "{\"error\":{\"message\":\"rate limit\"}}"));
    respuestas.add(new Respuesta(503, "{\"error\":{\"message\":\"overloaded\"}}"));
    respuestas.add(new Respuesta(200, exito(PRODUCTO_JSON, "end_turn")));

    ResultadoExtraccion resultado = extractor(4).extraer(texto());

    assertThat(resultado.productos().getFirst().titulo()).isEqualTo("Bolso de dama mediano");
    assertThat(peticiones).hasSize(3);
    assertThat(pausas).containsExactly(Duration.ofSeconds(1), Duration.ofSeconds(2));
  }

  @Test
  void agotadosLosIntentosFallaConElUltimoMotivo() {
    respuestas.add(new Respuesta(500, "{}"));
    respuestas.add(new Respuesta(500, "{}"));

    assertThatThrownBy(() -> extractor(2).extraer(texto()))
        .isInstanceOf(ExtraccionFallidaException.class)
        .hasMessageContaining("2 intentos")
        .hasMessageContaining("500");
    assertThat(peticiones).hasSize(2);
  }

  /** Repetir una petición inválida la vuelve a hacer inválida. */
  @Test
  void un400NoSeReintenta() {
    respuestas.add(new Respuesta(400, "{\"error\":{\"message\":\"schema is too complex\"}}"));

    assertThatThrownBy(() -> extractor(3).extraer(texto()))
        .isInstanceOf(ExtraccionFallidaException.class)
        .hasMessageContaining("400")
        .hasMessageContaining("schema is too complex");
    assertThat(peticiones).hasSize(1);
    assertThat(pausas).isEmpty();
  }

  @Test
  void unaNegativaOUnCortePorLongitudNoSonUnProducto() {
    respuestas.add(new Respuesta(200, exito("no puedo", "refusal")));
    assertThatThrownBy(() -> extractor(3).extraer(texto()))
        .isInstanceOf(ExtraccionFallidaException.class)
        .hasMessageContaining("se negó");

    respuestas.add(new Respuesta(200, exito("{\"es_producto\":tr", "max_tokens")));
    assertThatThrownBy(() -> extractor(3).extraer(texto()))
        .isInstanceOf(ExtraccionFallidaException.class)
        .hasMessageContaining("max-tokens");
  }

  @Test
  void loQueElMensajeNoDiceLlegaVacioYUnEnumeradoRaroCaeEnOtro() {
    respuestas.add(
        new Respuesta(
            200,
            exito(
                "{\"productos\":[{\"esta_agotado\":true,\"titulo\":null,\"linea\":\"otra\","
                    + "\"tipo\":\"chaleco\",\"precio_proveedor_cop\":null,"
                    + "\"tallas\":{\"tipo\":\"Lista\",\"sirve_hasta\":null,\"valores\":[\"M\",\"L\"]},"
                    + "\"cantidad_tonos\":null,\"tonos_nombrados\":[\"negro\"],\"material\":null,"
                    + "\"caracteristicas\":[],\"confianza\":0.5,\"notas\":\"sin foto\"}]}",
                "end_turn")));

    ResultadoExtraccion resultado = extractor(1).extraer(texto());

    assertThat(resultado.productos().getFirst().titulo()).isNull();
    assertThat(resultado.productos().getFirst().linea()).isNull();
    assertThat(resultado.productos().getFirst().tipo()).isEqualTo(TipoProductoProveedor.OTRO);
    assertThat(resultado.productos().getFirst().precioProveedor()).isNull();
    assertThat(resultado.productos().getFirst().estaAgotado()).isTrue();
    assertThat(resultado.productos().getFirst().tallas().tipo()).isEqualTo(TipoDeTalla.LISTA);
    assertThat(resultado.productos().getFirst().tallas().valores()).containsExactly("M", "L");
    assertThat(resultado.productos().getFirst().tonosNombrados()).containsExactly("negro");
    assertThat(resultado.productos().getFirst().notas()).isEqualTo("sin foto");
  }

  @Test
  void unBloqueQueNoEsJsonEsUnFallo() {
    respuestas.add(new Respuesta(200, exito("esto no es json", "end_turn")));

    assertThatThrownBy(() -> extractor(1).extraer(texto()))
        .isInstanceOf(ExtraccionFallidaException.class)
        .hasMessageContaining("JSON");
  }

  /** Violeta: el conjunto de chaqueta y jean, en el orden del mensaje. */
  @Test
  void variosProductosLleganEnElOrdenDelMensaje() {
    String jean =
        BOLSO.replace("Bolso de dama mediano", "Jean Mom Fit Licrado").replace("53000", "119900");
    respuestas.add(
        new Respuesta(200, exito("{\"productos\":[" + BOLSO + "," + jean + "]}", "end_turn")));

    ResultadoExtraccion resultado = extractor(1).extraer(texto());

    assertThat(resultado.productos())
        .extracting(p -> p.titulo())
        .containsExactly("Bolso de dama mediano", "Jean Mom Fit Licrado");
    assertThat(resultado.productos().get(1).precioProveedor()).isEqualTo(Dinero.deCop(119900));
    assertThat(resultado.productos()).allMatch(p -> p.esProducto());
  }

  /** Un saludo o una promoción: la lista vacía, sin fallo. */
  @Test
  void unMensajeSinProductosDevuelveLaListaVacia() {
    respuestas.add(new Respuesta(200, exito("{\"productos\":[]}", "end_turn")));

    assertThat(extractor(1).extraer(texto()).productos()).isEmpty();
  }

  /** La forma vieja, un solo objeto suelto, ya no es una respuesta válida. */
  @Test
  void sinLaListaDeProductosEsUnFallo() {
    respuestas.add(new Respuesta(200, exito(BOLSO, "end_turn")));

    assertThatThrownBy(() -> extractor(1).extraer(texto()))
        .isInstanceOf(ExtraccionFallidaException.class)
        .hasMessageContaining("lista de productos");
  }
}
