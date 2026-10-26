package co.tecnosport.api.infrastructure.proveedores.extraccion;

import co.tecnosport.api.application.proveedores.FotosParaLeer;
import co.tecnosport.api.application.proveedores.LectorDeFotos;
import co.tecnosport.api.domain.proveedores.LecturaDeFotos;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import javax.imageio.ImageIO;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * El lector de fotos contra la API de Claude: las fotos como bloques de imagen en base64 —la forma
 * documentada en platform.claude.com/docs/en/build-with-claude/vision— y la salida estructurada del
 * extractor ({@link ExtractorClaude}).
 *
 * <p><b>Las fotos se achican antes de salir</b> a {@link #LADO_MAYOR} píxeles por el lado mayor y
 * se mandan en JPEG: el pie impreso y una etiqueta se leen a ese tamaño, y una foto de WhatsApp a
 * tamaño completo cuesta el triple en tokens sin decir nada más. Una foto que no se puede
 * decodificar se salta; la lectura de las demás sigue sirviendo.
 *
 * <p><b>Dos modelos con dos formas de pedir</b> (ADR-0090, 26 de octubre de 2026). Haiku 4.5 lee
 * con temperatura 0 y sin razonar. El lector de calzado es Sonnet 5.5, que rechaza con un 400 una
 * temperatura distinta de la de siempre y razona siempre: a ese se le pide el esfuerzo, no la
 * temperatura.
 */
public final class LectorDeFotosClaude implements LectorDeFotos {

  /** El lado mayor de la foto que se manda. */
  static final int LADO_MAYOR = 1024;

  private final JsonMapper json = JsonMapper.builder().build();
  private final InterpreteDeLecturaDeFotosJson interprete = new InterpreteDeLecturaDeFotosJson();
  private final LlamadaAClaude llamada;
  private final int maxTokens;
  private final String promptDeSistema;
  private final JsonNode esquema;
  private final String esfuerzo;

  public LectorDeFotosClaude(
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
        esquemaJson,
        null);
  }

  /**
   * @param esfuerzo el de {@code output_config.effort} —«low», «medium», «high»— para un modelo que
   *     razona; nulo para Haiku 4.5, que no lo admite y lee con temperatura 0
   */
  public LectorDeFotosClaude(
      URI urlBase,
      String apiKey,
      String modelo,
      int maxTokens,
      Duration timeout,
      int intentos,
      Duration esperaInicial,
      String promptDeSistema,
      String esquemaJson,
      String esfuerzo) {
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
        esquemaJson,
        esfuerzo);
  }

  LectorDeFotosClaude(
      URI urlBase,
      String apiKey,
      String modelo,
      int maxTokens,
      Duration timeout,
      int intentos,
      Duration esperaInicial,
      LlamadaAClaude.Pausador pausador,
      String promptDeSistema,
      String esquemaJson,
      String esfuerzo) {
    this.llamada =
        new LlamadaAClaude(
            urlBase,
            apiKey,
            modelo,
            timeout,
            intentos,
            esperaInicial,
            pausador,
            "El lector de fotos");
    if (maxTokens <= 0) {
      throw new IllegalArgumentException("max-tokens tiene que ser positivo.");
    }
    this.maxTokens = maxTokens;
    this.promptDeSistema =
        LlamadaAClaude.exigir(promptDeSistema, "El prompt de sistema es obligatorio.");
    this.esquema = json.readTree(LlamadaAClaude.exigir(esquemaJson, "El esquema es obligatorio."));
    this.esfuerzo = esfuerzo == null || esfuerzo.isBlank() ? null : esfuerzo.strip();
  }

  @Override
  public Optional<LecturaDeFotos> leer(FotosParaLeer fotos) {
    List<Integer> enviadas = new ArrayList<>();
    String cuerpo = cuerpoDe(fotos, enviadas);
    if (enviadas.isEmpty()) {
      return Optional.empty();
    }
    LlamadaAClaude.Respuesta respuesta = llamada.enviar(cuerpo, "las fotos");
    return Optional.of(interprete.interpretar(respuesta.jsonCrudo(), enviadas::contains));
  }

  private String cuerpoDe(FotosParaLeer fotos, List<Integer> enviadas) {
    ObjectNode cuerpo = json.createObjectNode();
    cuerpo.put("model", llamada.modelo());
    cuerpo.put("max_tokens", maxTokens);
    // Temperatura 0: en tres corridas seguidas contra la verdad el lector leyó distinto las
    // mismas fotos (10 de octubre de 2026). Haiku 4.5 admite los parámetros de muestreo; los
    // modelos desde Opus 4.7 los rechazan con un 400. A esos se les pide el esfuerzo.
    if (esfuerzo == null) {
      cuerpo.put("temperature", 0);
    }
    cuerpo.put("system", promptDeSistema);
    ObjectNode mensaje = cuerpo.putArray("messages").addObject();
    mensaje.put("role", "user");
    ArrayNode contenido = mensaje.putArray("content");
    contenido.addObject().put("type", "text").put("text", presentacion(fotos));
    for (FotosParaLeer.FotoParaLeer foto : fotos.fotos()) {
      Optional<byte[]> jpeg = achicar(foto.bytes());
      if (jpeg.isEmpty()) {
        continue;
      }
      enviadas.add(foto.posicion());
      contenido.addObject().put("type", "text").put("text", "Foto " + (foto.posicion() + 1));
      ObjectNode imagen = contenido.addObject();
      imagen.put("type", "image");
      ObjectNode fuente = imagen.putObject("source");
      fuente.put("type", "base64");
      fuente.put("media_type", "image/jpeg");
      fuente.put("data", Base64.getEncoder().encodeToString(jpeg.get()));
    }
    ObjectNode configuracion = cuerpo.putObject("output_config");
    if (esfuerzo != null) {
      configuracion.put("effort", esfuerzo);
    }
    ObjectNode formato = configuracion.putObject("format");
    formato.put("type", "json_schema");
    formato.set("schema", esquema);
    return json.writeValueAsString(cuerpo);
  }

  private static String presentacion(FotosParaLeer fotos) {
    StringBuilder texto = new StringBuilder("Anuncio:\n").append(fotos.texto()).append("\n\n");
    texto.append("Productos que nombra el texto:\n");
    for (int i = 0; i < fotos.productos().size(); i++) {
      FotosParaLeer.ProductoNombrado producto = fotos.productos().get(i);
      texto
          .append(i + 1)
          .append(". ")
          .append(producto.titulo() == null ? "(sin título)" : producto.titulo());
      if (producto.codigo() != null) {
        texto.append(" (código ").append(producto.codigo()).append(')');
      }
      if (!producto.marcas().isEmpty()) {
        texto.append(" (marcas: ").append(String.join(", ", producto.marcas())).append(')');
      }
      texto.append('\n');
    }
    return texto.toString();
  }

  /**
   * La foto en JPEG con el lado mayor en {@link #LADO_MAYOR} o menos; vacía si no se decodifica.
   */
  static Optional<byte[]> achicar(byte[] original) {
    BufferedImage imagen;
    try {
      imagen = ImageIO.read(new ByteArrayInputStream(original));
    } catch (IOException | RuntimeException e) {
      return Optional.empty();
    }
    if (imagen == null) {
      return Optional.empty();
    }
    double escala =
        Math.min(1.0, (double) LADO_MAYOR / Math.max(imagen.getWidth(), imagen.getHeight()));
    int ancho = Math.max(1, (int) Math.round(imagen.getWidth() * escala));
    int alto = Math.max(1, (int) Math.round(imagen.getHeight() * escala));
    BufferedImage destino = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB);
    Graphics2D lienzo = destino.createGraphics();
    try {
      lienzo.setRenderingHint(
          RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
      lienzo.drawImage(imagen, 0, 0, ancho, alto, null);
    } finally {
      lienzo.dispose();
    }
    ByteArrayOutputStream salida = new ByteArrayOutputStream();
    try {
      if (!ImageIO.write(destino, "jpg", salida)) {
        return Optional.empty();
      }
    } catch (IOException e) {
      return Optional.empty();
    }
    return Optional.of(salida.toByteArray());
  }
}
