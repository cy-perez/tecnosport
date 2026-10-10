package co.tecnosport.api.infrastructure.proveedores.extraccion;

import static org.assertj.core.api.Assertions.assertThat;

import co.tecnosport.api.application.catalogo.UrlFirmada;
import co.tecnosport.api.application.proveedores.AlmacenDeArchivosDeProveedor;
import co.tecnosport.api.application.proveedores.ChatExportado;
import co.tecnosport.api.application.proveedores.ExtraccionEvaluada;
import co.tecnosport.api.application.proveedores.ExtraerProductoDePublicacion;
import co.tecnosport.api.application.proveedores.MensajeCrudo;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.proveedores.AgrupadorDePublicaciones;
import co.tecnosport.api.domain.proveedores.IdExternoDeMensaje;
import co.tecnosport.api.domain.proveedores.MensajeProveedor;
import co.tecnosport.api.domain.proveedores.OrdenDePublicacion;
import co.tecnosport.api.domain.proveedores.PublicacionProveedor;
import co.tecnosport.api.domain.proveedores.TipoMensaje;
import co.tecnosport.api.infrastructure.proveedores.whatsapp.ExportacionChatWhatsApp;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * La ingesta de verdad —el lector del zip, el agrupador, el extractor, el lector de fotos y el
 * reparto— contra las exportaciones de La Riverah y Violeta del 9 de octubre de 2026 y lo que una
 * persona leyó en sus fotos ({@code ingesta/verdad.json}). <b>No corre en el pipeline</b>: pide
 * {@code ANTHROPIC_API_KEY}, la etiqueta {@code integracion-externa} y los zips, que no se
 * versionan porque son de un tercero. Cuesta unos centavos de dólar por corrida.
 *
 * <p>Falla si una publicación da un número de productos fuera de lo esperado o si a un producto de
 * Violeta le tocan fotos que no son las suyas. El resto —SKU leídos, tallas por tono, precios
 * adicionales— va al informe de {@code build/reports/ingesta-contra-verdad.md}, que es para mirar.
 *
 * <pre>
 * gradlew.bat :infrastructure:test --tests "*IngestaRealContraVerdadTest" -PintegracionExterna=true
 * </pre>
 */
@Tag("integracion-externa")
@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
@EnabledIf("hayVerdad")
class IngestaRealContraVerdadTest {

  private static final Path CARPETA = Path.of("..", "..", "..", "ingesta");
  private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
  private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

  static boolean hayVerdad() {
    return Files.exists(CARPETA.resolve("verdad.json"));
  }

  @Test
  void cadaPublicacionDaLosProductosYLasFotosQueSonSuyas() throws IOException {
    JsonNode verdad =
        JsonMapper.builder().build().readTree(CARPETA.resolve("verdad.json").toFile());
    List<String> fallos = new ArrayList<>();
    StringBuilder informe = new StringBuilder("# Ingesta contra la verdad\n\n");
    for (JsonNode proveedor : verdad.path("proveedores")) {
      evaluar(proveedor, fallos, informe);
    }
    Path salida = Path.of("build", "reports", "ingesta-contra-verdad.md");
    Files.createDirectories(salida.getParent());
    Files.writeString(salida, informe, StandardCharsets.UTF_8);

    assertThat(fallos).as("ver %s", salida.toAbsolutePath()).isEmpty();
  }

  private void evaluar(JsonNode proveedor, List<String> fallos, StringBuilder informe)
      throws IOException {
    String nombre = proveedor.path("remitente").asString();
    informe.append("## ").append(nombre).append("\n\n");
    AlmacenEnMemoria almacen = new AlmacenEnMemoria();
    almacen.guardar(
        "zip",
        "application/zip",
        Files.readAllBytes(CARPETA.resolve(proveedor.path("zip").asString())));
    ChatExportado chat = new ExportacionChatWhatsApp(almacen, 500_000_000L).leer("zip", null);

    UUID proveedorId = UUID.randomUUID();
    UUID loteId = UUID.randomUUID();
    Map<UUID, MensajeProveedor> mensajes = new LinkedHashMap<>();
    Map<UUID, String> archivoDe = new HashMap<>();
    int i = 0;
    for (MensajeCrudo crudo : chat.mensajes()) {
      if (!nombre.equals(crudo.remitente())) {
        continue;
      }
      IdExternoDeMensaje id = new IdExternoDeMensaje("m" + (++i));
      MensajeProveedor mensaje;
      if (crudo.tipo() == TipoMensaje.IMAGEN && crudo.adjunto() != null) {
        String archivo = crudo.adjunto().nombre();
        almacen.guardar(archivo, crudo.adjunto().contentType(), crudo.adjunto().bytes());
        mensaje =
            MensajeProveedor.imagen(
                proveedorId, loteId, id, crudo.enviadoEn(), crudo.pieDeFoto(), archivo);
        archivoDe.put(mensaje.id(), archivo);
      } else if (crudo.tipo() == TipoMensaje.TEXTO) {
        mensaje = MensajeProveedor.texto(proveedorId, loteId, id, crudo.enviadoEn(), crudo.texto());
      } else {
        continue;
      }
      mensajes.put(mensaje.id(), mensaje);
    }
    List<PublicacionProveedor> publicaciones =
        new AgrupadorDePublicaciones(Duration.ofMinutes(15))
            .agrupar(
                List.copyOf(mensajes.values()),
                OrdenDePublicacion.valueOf(proveedor.path("orden").asString()))
            .publicaciones();

    ExtraerProductoDePublicacion extraer =
        new ExtraerProductoDePublicacion(extractor(), lector(), almacen, new BigDecimal("0.75"));
    Map<String, Integer> vistasPorHora = new HashMap<>();
    for (PublicacionProveedor publicacion : publicaciones) {
      String hora = publicacion.fecha().atZone(BOGOTA).format(HORA);
      int indice = vistasPorHora.merge(hora, 1, Integer::sum) - 1;
      Optional<JsonNode> esperada = esperadaDe(proveedor, hora, indice);
      List<ExtraccionEvaluada> evaluadas =
          extraer.ejecutar(
              publicacion, mensajes, LineaCatalogo.valueOf(proveedor.path("linea").asString()));
      informe
          .append("### ")
          .append(hora)
          .append(" — ")
          .append(evaluadas.size())
          .append(" productos\n\n");
      for (ExtraccionEvaluada evaluada : evaluadas) {
        List<String> fotos = fotosDe(evaluada, publicacion, archivoDe);
        informe
            .append("- ")
            .append(evaluada.producto().titulo())
            .append(" · código ")
            .append(evaluada.producto().codigoReferenciaOpcional().orElse("—"))
            .append(" · ")
            .append(evaluada.precioProveedorOpcional().map(Dinero::valor).orElse(null))
            .append(" · tallas ")
            .append(evaluada.producto().tallas().valores())
            .append(" · por tono ")
            .append(evaluada.producto().tallasPorTono().tonos())
            .append(" · adicionales ")
            .append(evaluada.producto().preciosAdicionales().size())
            .append(" · alertas ")
            .append(evaluada.alertas())
            .append("\n  - fotos ")
            .append(fotos)
            .append("\n  - tonos ")
            .append(evaluada.reparto().tonosSugeridos().values())
            .append('\n');
      }
      informe.append('\n');
      esperada.ifPresentOrElse(
          e -> comparar(nombre, hora, e, evaluadas, publicacion, archivoDe, fallos, informe),
          () -> informe.append("_(sin verdad para esta publicación)_\n\n"));
    }
  }

  private static Optional<JsonNode> esperadaDe(JsonNode proveedor, String hora, int indice) {
    for (JsonNode publicacion : proveedor.path("publicaciones")) {
      if (publicacion.path("hora").asString().equals(hora)
          && publicacion.path("indice").asInt(0) == indice) {
        return Optional.of(publicacion);
      }
    }
    return Optional.empty();
  }

  private static void comparar(
      String proveedor,
      String hora,
      JsonNode esperada,
      List<ExtraccionEvaluada> evaluadas,
      PublicacionProveedor publicacion,
      Map<UUID, String> archivoDe,
      List<String> fallos,
      StringBuilder informe) {
    String donde = proveedor + " " + hora;
    if (esperada.has("productos")) {
      JsonNode productos = esperada.path("productos");
      if (productos.size() != evaluadas.size()) {
        fallos.add(
            donde + ": " + evaluadas.size() + " productos, se esperaban " + productos.size());
        return;
      }
      for (JsonNode producto : productos) {
        String codigo = producto.path("codigo").asString();
        Optional<ExtraccionEvaluada> suya =
            evaluadas.stream()
                .filter(
                    e -> e.producto().codigoReferenciaOpcional().map(codigo::equals).orElse(false))
                .findFirst();
        if (suya.isEmpty()) {
          fallos.add(donde + ": ningún producto con el código " + codigo);
          continue;
        }
        Set<String> esperadas = new java.util.TreeSet<>();
        producto.path("fotos").forEach(f -> esperadas.add(f.asString()));
        Set<String> obtenidas =
            new java.util.TreeSet<>(fotosDe(suya.get(), publicacion, archivoDe));
        if (!esperadas.equals(obtenidas)) {
          fallos.add(donde + " " + codigo + ": fotos " + obtenidas + ", se esperaban " + esperadas);
        }
        if (producto.has("tallasPorTono")) {
          informe
              .append("- tallas por tono de ")
              .append(codigo)
              .append(": esperadas ")
              .append(producto.path("tallasPorTono"))
              .append(", leídas ")
              .append(suya.get().producto().tallasPorTono().tonos())
              .append('\n');
        }
      }
      return;
    }
    int minimo = esperada.path("minimo").asInt();
    int maximo = esperada.path("maximo").asInt();
    if (evaluadas.size() < minimo || evaluadas.size() > maximo) {
      fallos.add(
          donde + ": " + evaluadas.size() + " productos, se esperaban " + minimo + "–" + maximo);
    }
    if (esperada.has("precio")) {
      long precio = esperada.path("precio").asLong();
      boolean todos =
          evaluadas.stream()
              .allMatch(
                  e ->
                      e.precioProveedorOpcional()
                          .map(p -> p.equals(Dinero.deCop(precio)))
                          .orElse(false));
      if (!todos) {
        fallos.add(donde + ": algún producto sin el precio " + precio);
      }
    }
    if (esperada.has("skus")) {
      String lectura =
          evaluadas.isEmpty() ? "" : String.valueOf(evaluadas.getFirst().reparto().lecturaCruda());
      List<String> skus = new ArrayList<>();
      esperada.path("skus").forEach(s -> skus.add(s.asString()));
      List<String> leidos = skus.stream().filter(lectura::contains).toList();
      informe
          .append("- SKU del pie leídos: ")
          .append(leidos.size())
          .append(" de ")
          .append(skus.size())
          .append(
              skus.size() == leidos.size()
                  ? ""
                  : " (faltan "
                      + skus.stream()
                          .filter(s -> !leidos.contains(s))
                          .collect(Collectors.joining(", "))
                      + ")")
          .append('\n');
    }
    if (esperada.has("adicionales")) {
      informe
          .append("- precios adicionales: esperados ")
          .append(esperada.path("adicionales").asInt())
          .append(", leídos ")
          .append(
              evaluadas.isEmpty() ? 0 : evaluadas.getFirst().producto().preciosAdicionales().size())
          .append('\n');
    }
    informe.append('\n');
  }

  private static List<String> fotosDe(
      ExtraccionEvaluada evaluada, PublicacionProveedor publicacion, Map<UUID, String> archivoDe) {
    List<UUID> fotos = evaluada.fotos().isEmpty() ? publicacion.medios() : evaluada.fotos();
    return fotos.stream().map(archivoDe::get).filter(java.util.Objects::nonNull).toList();
  }

  private static ExtractorClaude extractor() {
    return new ExtractorClaude(
        URI.create("https://api.anthropic.com"),
        System.getenv("ANTHROPIC_API_KEY"),
        "claude-haiku-4-5-20251001",
        2048,
        Duration.ofSeconds(60),
        3,
        Duration.ofSeconds(2),
        RecursosDelExtractor.promptDeSistema(),
        RecursosDelExtractor.esquema());
  }

  private static LectorDeFotosClaude lector() {
    return new LectorDeFotosClaude(
        URI.create("https://api.anthropic.com"),
        System.getenv("ANTHROPIC_API_KEY"),
        "claude-haiku-4-5-20251001",
        4096,
        Duration.ofSeconds(120),
        3,
        Duration.ofSeconds(2),
        RecursosDelExtractor.promptDelLector(),
        RecursosDelExtractor.esquemaDelLector());
  }

  /** El bucket privado en memoria: el zip y cada foto, por nombre. */
  private static final class AlmacenEnMemoria implements AlmacenDeArchivosDeProveedor {
    private final Map<String, byte[]> objetos = new HashMap<>();

    @Override
    public UrlFirmada generarUrlDeSubida(String objectKey, String contentType) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Optional<Long> tamanoBytes(String objectKey) {
      return Optional.ofNullable(objetos.get(objectKey)).map(b -> (long) b.length);
    }

    @Override
    public void guardar(String objectKey, String contentType, byte[] bytes) {
      objetos.put(objectKey, bytes);
    }

    @Override
    public Optional<byte[]> leer(String objectKey) {
      return Optional.ofNullable(objetos.get(objectKey));
    }

    @Override
    public UrlFirmada urlDeLectura(String objectKey) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void borrar(String objectKey) {
      objetos.remove(objectKey);
    }
  }
}
