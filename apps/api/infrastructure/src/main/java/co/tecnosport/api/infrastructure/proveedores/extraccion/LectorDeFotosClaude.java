package co.tecnosport.api.infrastructure.proveedores.extraccion;

import co.tecnosport.api.application.proveedores.ExtraccionFallidaException;
import co.tecnosport.api.application.proveedores.FotosParaLeer;
import co.tecnosport.api.application.proveedores.LectorDeFotos;
import co.tecnosport.api.domain.proveedores.LecturaDeFotos;
import co.tecnosport.api.domain.proveedores.LecturaDeFotos.LecturaDeFoto;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
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
 */
public final class LectorDeFotosClaude implements LectorDeFotos {

  /** El lado mayor de la foto que se manda. */
  static final int LADO_MAYOR = 1024;

  private static final DateTimeFormatter FECHA_DEL_PIE = DateTimeFormatter.ofPattern("d/M/uuuu");

  private final JsonMapper json = JsonMapper.builder().build();
  private final LlamadaAClaude llamada;
  private final int maxTokens;
  private final String promptDeSistema;
  private final JsonNode esquema;

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
        esquemaJson);
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
      String esquemaJson) {
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
  }

  @Override
  public Optional<LecturaDeFotos> leer(FotosParaLeer fotos) {
    List<Integer> enviadas = new ArrayList<>();
    String cuerpo = cuerpoDe(fotos, enviadas);
    if (enviadas.isEmpty()) {
      return Optional.empty();
    }
    LlamadaAClaude.Respuesta respuesta = llamada.enviar(cuerpo, "las fotos");
    return Optional.of(aLectura(respuesta.jsonCrudo(), enviadas));
  }

  private String cuerpoDe(FotosParaLeer fotos, List<Integer> enviadas) {
    ObjectNode cuerpo = json.createObjectNode();
    cuerpo.put("model", llamada.modelo());
    cuerpo.put("max_tokens", maxTokens);
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
    ObjectNode formato = cuerpo.putObject("output_config").putObject("format");
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

  /**
   * Del JSON del esquema a la lectura. Una foto con un número que no se mandó se descarta: el
   * modelo no puede hablar de una foto que no vio.
   */
  private LecturaDeFotos aLectura(String jsonCrudo, List<Integer> enviadas) {
    JsonNode raiz;
    try {
      raiz = json.readTree(jsonCrudo);
    } catch (RuntimeException e) {
      throw new ExtraccionFallidaException("El lector de fotos no devolvió un JSON legible.", e);
    }
    JsonNode fotos = raiz.path("fotos");
    if (!raiz.isObject() || !fotos.isArray()) {
      throw new ExtraccionFallidaException("El lector de fotos no devolvió la lista de fotos.");
    }
    List<LecturaDeFoto> lecturas = new ArrayList<>();
    List<Integer> vistas = new ArrayList<>();
    for (JsonNode foto : fotos) {
      int posicion = foto.path("foto").asInt(0) - 1;
      if (!enviadas.contains(posicion) || vistas.contains(posicion)) {
        continue;
      }
      vistas.add(posicion);
      List<LecturaDeFoto.BloqueDePie> pie = new ArrayList<>();
      for (JsonNode bloque : foto.path("pie")) {
        pie.add(
            new LecturaDeFoto.BloqueDePie(
                texto(bloque.path("sku")),
                fecha(texto(bloque.path("fecha"))),
                lista(bloque.path("tallas"))));
      }
      lecturas.add(
          LecturaDeFoto.conPie(
              posicion,
              lista(foto.path("codigos")),
              pie,
              lista(foto.path("colores")),
              texto(foto.path("diseno"))));
    }
    return new LecturaDeFotos(raiz.path("album_de_disenos").asBoolean(false), lecturas, jsonCrudo);
  }

  /** «09/10/2026», como lo imprime La Riverah; nula si no se lee así. */
  private static LocalDate fecha(String texto) {
    if (texto == null) {
      return null;
    }
    try {
      return LocalDate.parse(texto.strip(), FECHA_DEL_PIE);
    } catch (DateTimeParseException e) {
      return null;
    }
  }

  private static String texto(JsonNode nodo) {
    if (nodo == null || !nodo.isString()) {
      return null;
    }
    String valor = nodo.asString();
    return valor == null || valor.isBlank() ? null : valor;
  }

  private static List<String> lista(JsonNode nodo) {
    List<String> valores = new ArrayList<>();
    if (nodo != null && nodo.isArray()) {
      for (JsonNode elemento : nodo) {
        String valor = texto(elemento);
        if (valor != null) {
          valores.add(valor);
        }
      }
    }
    return valores;
  }
}
