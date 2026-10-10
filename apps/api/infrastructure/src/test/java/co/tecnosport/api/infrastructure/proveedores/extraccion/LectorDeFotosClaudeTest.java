package co.tecnosport.api.infrastructure.proveedores.extraccion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.tecnosport.api.application.proveedores.ExtraccionFallidaException;
import co.tecnosport.api.application.proveedores.FotosParaLeer;
import co.tecnosport.api.application.proveedores.FotosParaLeer.FotoParaLeer;
import co.tecnosport.api.application.proveedores.FotosParaLeer.ProductoNombrado;
import co.tecnosport.api.domain.proveedores.LecturaDeFotos;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Contra un servidor HTTP de prueba del JDK, como {@link ExtractorClaudeTest}: la forma de la
 * petición con las fotos, cómo se achican y cómo se lee la respuesta. Que la API acepte el esquema
 * lo comprueba {@link LectorDeFotosClaudeContratoTest}, que no corre en el pipeline.
 */
class LectorDeFotosClaudeTest {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  private HttpServer servidor;
  private final Deque<String> respuestas = new ArrayDeque<>();
  private final List<JsonNode> peticiones = new ArrayList<>();

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
    byte[] cuerpo =
        (respuestas.isEmpty() ? "{}" : respuestas.poll()).getBytes(StandardCharsets.UTF_8);
    intercambio.getResponseHeaders().add("Content-Type", "application/json");
    intercambio.sendResponseHeaders(200, cuerpo.length);
    try (OutputStream salida = intercambio.getResponseBody()) {
      salida.write(cuerpo);
    }
  }

  private static String exito(String texto) {
    return "{\"model\":\"claude-haiku-4-5-20251001\",\"stop_reason\":\"end_turn\","
        + "\"content\":[{\"type\":\"text\",\"text\":"
        + JSON.writeValueAsString(texto)
        + "}],\"usage\":{\"input_tokens\":4100,\"output_tokens\":210}}";
  }

  private LectorDeFotosClaude lector() {
    return new LectorDeFotosClaude(
        URI.create("http://127.0.0.1:" + servidor.getAddress().getPort()),
        "sk-prueba",
        "claude-haiku-4-5-20251001",
        4096,
        Duration.ofSeconds(5),
        1,
        Duration.ofSeconds(1),
        duracion -> {},
        RecursosDelExtractor.promptDelLector(),
        RecursosDelExtractor.esquemaDelLector());
  }

  private static byte[] jpeg(int ancho, int alto) throws IOException {
    BufferedImage imagen = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB);
    ByteArrayOutputStream salida = new ByteArrayOutputStream();
    ImageIO.write(imagen, "jpg", salida);
    return salida.toByteArray();
  }

  private static FotosParaLeer violeta(List<FotoParaLeer> fotos) {
    return new FotosParaLeer(
        "Body manga larga (VY3010) 💲38\nJean baggy (Q355) 💲128",
        List.of(
            new ProductoNombrado("Bodi manga larga", "VY3010"),
            new ProductoNombrado("Jean baggy", "Q355")),
        fotos);
  }

  @Test
  void mandaCadaFotoRotuladaComoImagenEnBase64YLeeLaLectura() throws IOException {
    respuestas.add(
        exito(
            "{\"album_de_disenos\":false,\"fotos\":["
                + "{\"foto\":1,\"codigos\":[\"B:VY3010\",\"J:Q355\"],\"pie\":[],"
                + "\"colores\":[\"negro\",\"gris\"],\"diseno\":\"bodi\"},"
                + "{\"foto\":3,\"codigos\":[\"VY3010\"],\"pie\":[],"
                + "\"colores\":[\"cocoa\"],\"diseno\":\"bodi\"}]}"));

    Optional<LecturaDeFotos> lectura =
        lector()
            .leer(
                violeta(
                    List.of(new FotoParaLeer(0, jpeg(40, 30)), new FotoParaLeer(2, jpeg(30, 40)))));

    JsonNode contenido = peticiones.get(0).path("messages").get(0).path("content");
    assertThat(contenido.get(0).path("text").asString())
        .contains("Body manga larga (VY3010)")
        .contains("1. Bodi manga larga (código VY3010)")
        .contains("2. Jean baggy (código Q355)");
    assertThat(contenido.get(1).path("text").asString()).isEqualTo("Foto 1");
    assertThat(contenido.get(2).path("type").asString()).isEqualTo("image");
    assertThat(contenido.get(2).path("source").path("type").asString()).isEqualTo("base64");
    assertThat(contenido.get(2).path("source").path("media_type").asString())
        .isEqualTo("image/jpeg");
    assertThat(contenido.get(3).path("text").asString()).isEqualTo("Foto 3");
    assertThat(peticiones.get(0).path("system").asString()).contains("Nunca inventes");
    assertThat(peticiones.get(0).path("temperature").isNumber()).isTrue();
    assertThat(peticiones.get(0).path("temperature").asInt(-1)).isZero();

    assertThat(lectura).isPresent();
    assertThat(lectura.get().fotos()).hasSize(2);
    assertThat(lectura.get().deLaFoto(0).orElseThrow().codigos()).containsExactly("VY3010", "Q355");
    assertThat(lectura.get().deLaFoto(2).orElseThrow().colores()).containsExactly("cocoa");
  }

  /** Y del pie con dos bloques vale el de la fecha más reciente, no el que va primero. */
  @Test
  void unaFotoQueElModeloNoVioNoEntraALaLectura() throws IOException {
    respuestas.add(
        exito(
            "{\"album_de_disenos\":false,\"fotos\":["
                + "{\"foto\":7,\"codigos\":[\"VY3010\"],\"pie\":[],"
                + "\"colores\":[],\"diseno\":null},"
                + "{\"foto\":1,\"codigos\":[],\"pie\":[{\"sku\":\"RV101862\",\"fecha\":\"02/10/2026\",\"tallas\":[\"S\",\"M\",\"L\",\"XL\"]},{\"sku\":\"RV102347\",\"fecha\":\"9/10/2026\",\"tallas\":[\"s\"]}],"
                + "\"colores\":[],\"diseno\":null},"
                + "{\"foto\":1,\"codigos\":[\"OTRA\"],\"pie\":[],"
                + "\"colores\":[],\"diseno\":null}]}"));

    LecturaDeFotos lectura =
        lector().leer(violeta(List.of(new FotoParaLeer(0, jpeg(10, 10))))).orElseThrow();

    assertThat(lectura.fotos()).hasSize(1);
    assertThat(lectura.fotos().getFirst().sku()).isEqualTo("RV102347");
    assertThat(lectura.fotos().getFirst().tallasDelPie()).containsExactly("S");
  }

  @Test
  void sinNingunaFotoLegibleNoSeLlamaALaApi() {
    Optional<LecturaDeFotos> lectura =
        lector().leer(violeta(List.of(new FotoParaLeer(0, new byte[] {1, 2, 3}))));

    assertThat(lectura).isEmpty();
    assertThat(peticiones).isEmpty();
  }

  @Test
  void unaFotoGrandeSeAchicaAntesDeSalir() throws IOException {
    respuestas.add(exito("{\"album_de_disenos\":false,\"fotos\":[]}"));

    lector().leer(violeta(List.of(new FotoParaLeer(0, jpeg(3000, 1500)))));

    String base64 =
        peticiones
            .get(0)
            .path("messages")
            .get(0)
            .path("content")
            .get(2)
            .path("source")
            .path("data")
            .asString();
    BufferedImage enviada =
        ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(base64)));
    assertThat(enviada.getWidth()).isEqualTo(LectorDeFotosClaude.LADO_MAYOR);
    assertThat(enviada.getHeight()).isEqualTo(LectorDeFotosClaude.LADO_MAYOR / 2);
  }

  @Test
  void unJsonSinListaDeFotosEsUnFallo() throws IOException {
    respuestas.add(exito("{\"album_de_disenos\":true}"));

    assertThatThrownBy(() -> lector().leer(violeta(List.of(new FotoParaLeer(0, jpeg(5, 5))))))
        .isInstanceOf(ExtraccionFallidaException.class)
        .hasMessageContaining("lista de fotos");
  }
}
