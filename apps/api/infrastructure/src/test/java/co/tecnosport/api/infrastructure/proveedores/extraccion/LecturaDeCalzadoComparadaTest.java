package co.tecnosport.api.infrastructure.proveedores.extraccion;

import static org.assertj.core.api.Assertions.assertThat;

import co.tecnosport.api.application.proveedores.ChatExportado;
import co.tecnosport.api.application.proveedores.ExtraccionEvaluada;
import co.tecnosport.api.application.proveedores.ExtraerProductoDePublicacion;
import co.tecnosport.api.application.proveedores.FotosParaLeer;
import co.tecnosport.api.application.proveedores.LectorDeFotos;
import co.tecnosport.api.application.proveedores.MensajeCrudo;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.proveedores.AgrupadorDePublicaciones;
import co.tecnosport.api.domain.proveedores.AlertaBorrador;
import co.tecnosport.api.domain.proveedores.IdExternoDeMensaje;
import co.tecnosport.api.domain.proveedores.LecturaDeCalzado;
import co.tecnosport.api.domain.proveedores.LecturaDeFotos;
import co.tecnosport.api.domain.proveedores.MensajeProveedor;
import co.tecnosport.api.domain.proveedores.OrdenDePublicacion;
import co.tecnosport.api.domain.proveedores.PublicacionProveedor;
import co.tecnosport.api.domain.proveedores.TipoMensaje;
import co.tecnosport.api.infrastructure.proveedores.extraccion.IngestaRealContraVerdadTest.AlmacenEnMemoria;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * <b>El experimento de ADR-0090</b>: la ingesta de verdad de una exportación de Tenis Al Pie —el
 * lector del zip, el agrupador, el extractor, el lector de fotos y el reparto por tenis— con cada
 * modelo del lector, lado a lado. No falla por lo que lee: deja el informe en {@code
 * build/reports/lectura-calzado-comparada.md}, para que una persona compare los títulos, los grupos
 * y las fotos que se descartaron contra lo que ve en el zip.
 *
 * <p><b>No corre en el pipeline</b>: pide {@code ANTHROPIC_API_KEY}, la etiqueta {@code
 * integracion-externa} y el zip, que no se versiona porque es de un tercero. Gasta de verdad: cada
 * modelo lee todas las fotos, y los tokens de cada lectura quedan en la salida estándar.
 *
 * <pre>
 * gradlew.bat :infrastructure:test --tests "*LecturaDeCalzadoComparadaTest" -PintegracionExterna=true
 * </pre>
 *
 * <ul>
 *   <li>{@code TS_ZIP_CALZADO}: el zip; por omisión {@code ingesta/tenis-al-pie.zip} en la raíz.
 *   <li>{@code TS_REMITENTE_CALZADO}: quien manda en el chat; por omisión {@code tenis.alpie}.
 *   <li>{@code TS_MODELOS_CALZADO}: los modelos separados por coma, cada uno con su esfuerzo detrás
 *       de dos puntos si razona; por omisión {@code
 *       claude-haiku-4-5-20251001,claude-sonnet-5-5:low}.
 * </ul>
 */
@Tag("integracion-externa")
@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
@EnabledIf("hayZip")
class LecturaDeCalzadoComparadaTest {

  private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
  private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("dd/MM HH:mm");

  static Path zip() {
    return Optional.ofNullable(System.getenv("TS_ZIP_CALZADO"))
        .filter(z -> !z.isBlank())
        .map(Path::of)
        .orElse(Path.of("..", "..", "..", "ingesta", "tenis-al-pie.zip"));
  }

  static boolean hayZip() {
    return Files.exists(zip());
  }

  @Test
  void cadaModeloLeeLaMismaExportacion() throws IOException {
    String remitente =
        Objects.requireNonNullElse(System.getenv("TS_REMITENTE_CALZADO"), "tenis.alpie");
    String modelos =
        Objects.requireNonNullElse(
            System.getenv("TS_MODELOS_CALZADO"), "claude-haiku-4-5-20251001,claude-sonnet-5-5:low");

    AlmacenEnMemoria almacen = new AlmacenEnMemoria();
    almacen.guardar("zip", "application/zip", Files.readAllBytes(zip()));
    ChatExportado chat = new ExportacionChatWhatsApp(almacen, 500_000_000L).leer("zip", null);
    UUID proveedorId = UUID.randomUUID();
    UUID loteId = UUID.randomUUID();
    Map<UUID, MensajeProveedor> mensajes = new LinkedHashMap<>();
    Map<UUID, String> archivoDe = new HashMap<>();
    int i = 0;
    for (MensajeCrudo crudo : chat.mensajes()) {
      if (!remitente.equals(crudo.remitente())) {
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
            .agrupar(List.copyOf(mensajes.values()), OrdenDePublicacion.FOTOS_PRIMERO)
            .publicaciones();

    StringBuilder informe =
        new StringBuilder("# Lectura del calzado, modelo contra modelo\n\n")
            .append("Exportación: `")
            .append(zip().getFileName())
            .append("` · ")
            .append(publicaciones.size())
            .append(" publicaciones\n\n");
    StringBuilder resumen =
        new StringBuilder(
            "| Modelo | Fotos | Pasan | Borradores | Con marca | Con modelo | Por confirmar |"
                + " Sin marca | Lectura (s) |\n|---|---|---|---|---|---|---|---|---|\n");
    StringBuilder detalle = new StringBuilder();
    for (String conEsfuerzo : modelos.split(",")) {
      String[] partes = conEsfuerzo.strip().split(":");
      String modelo = partes[0];
      String esfuerzo = partes.length > 1 ? partes[1] : null;
      Cronometrado lector = new Cronometrado(lector(modelo, esfuerzo));
      ExtraerProductoDePublicacion extraer =
          new ExtraerProductoDePublicacion(
              extractor(), lector, lector, almacen, new BigDecimal("0.75"));
      Totales totales = new Totales();
      detalle.append("## ").append(conEsfuerzo).append("\n\n");
      for (PublicacionProveedor publicacion : publicaciones) {
        List<ExtraccionEvaluada> evaluadas =
            extraer.ejecutar(publicacion, mensajes, LineaCatalogo.CALZADO, true);
        anotar(publicacion, evaluadas, archivoDe, totales, detalle);
      }
      resumen
          .append("| ")
          .append(conEsfuerzo)
          .append(" | ")
          .append(totales.fotos)
          .append(" | ")
          .append(totales.pasan)
          .append(" | ")
          .append(totales.borradores)
          .append(" | ")
          .append(totales.conMarca)
          .append(" | ")
          .append(totales.conModelo)
          .append(" | ")
          .append(totales.porConfirmar)
          .append(" | ")
          .append(totales.borradores - totales.conMarca)
          .append(" | ")
          .append(lector.milis / 1000)
          .append(" |\n");
    }
    informe.append(resumen).append('\n').append(detalle);
    Path salida = Path.of("build", "reports", "lectura-calzado-comparada.md");
    Files.createDirectories(salida.getParent());
    Files.writeString(salida, informe, StandardCharsets.UTF_8);

    assertThat(salida).exists();
  }

  private static void anotar(
      PublicacionProveedor publicacion,
      List<ExtraccionEvaluada> evaluadas,
      Map<UUID, String> archivoDe,
      Totales totales,
      StringBuilder detalle) {
    List<UUID> medios = publicacion.medios();
    totales.fotos += medios.size();
    detalle
        .append("### ")
        .append(publicacion.fecha().atZone(BOGOTA).format(HORA))
        .append(" — ")
        .append(medios.size())
        .append(" fotos, ")
        .append(evaluadas.size())
        .append(" borradores\n\n");
    Set<UUID> pasaron = new HashSet<>();
    for (ExtraccionEvaluada evaluada : evaluadas) {
      totales.borradores++;
      pasaron.addAll(evaluada.fotos());
      Optional<LecturaDeCalzado> calzado = evaluada.calzadoOpcional();
      boolean conMarca =
          !evaluada.producto().marcas().isEmpty()
              || calzado.flatMap(LecturaDeCalzado::marcaOpcional).isPresent();
      totales.conMarca += conMarca ? 1 : 0;
      totales.conModelo +=
          calzado.flatMap(LecturaDeCalzado::modeloParaElTitulo).isPresent() ? 1 : 0;
      boolean porConfirmar = evaluada.alertas().contains(AlertaBorrador.MODELO_POR_CONFIRMAR);
      totales.porConfirmar += porConfirmar ? 1 : 0;
      detalle
          .append("- ")
          .append(evaluada.producto().titulo())
          .append(porConfirmar ? " _(por confirmar)_" : "")
          .append(" · ")
          .append(calzado.flatMap(LecturaDeCalzado::generoOpcional).map(Enum::name).orElse("—"))
          .append(" · tonos ")
          .append(evaluada.producto().tonosNombrados())
          .append("\n  - ")
          .append(evaluada.fotos().stream().map(archivoDe::get).toList())
          .append('\n');
    }
    totales.pasan += pasaron.size();
    List<String> descartadas =
        medios.stream()
            .filter(m -> !pasaron.contains(m))
            .map(archivoDe::get)
            .filter(Objects::nonNull)
            .toList();
    if (!descartadas.isEmpty()) {
      detalle.append("- _descartadas:_ ").append(descartadas).append('\n');
    }
    detalle.append('\n');
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

  private static LectorDeFotosClaude lector(String modelo, String esfuerzo) {
    return new LectorDeFotosClaude(
        URI.create("https://api.anthropic.com"),
        System.getenv("ANTHROPIC_API_KEY"),
        modelo,
        esfuerzo == null ? 8192 : 16000,
        Duration.ofSeconds(150),
        3,
        Duration.ofSeconds(2),
        RecursosDelExtractor.promptDelLector(),
        RecursosDelExtractor.esquemaDelLector(),
        esfuerzo);
  }

  /** Cuánto tardan las lecturas de un modelo, sumadas. */
  private static final class Cronometrado implements LectorDeFotos {
    private final LectorDeFotos lector;
    private long milis;

    Cronometrado(LectorDeFotos lector) {
      this.lector = lector;
    }

    @Override
    public Optional<LecturaDeFotos> leer(FotosParaLeer fotos) {
      long inicio = System.nanoTime();
      try {
        return lector.leer(fotos);
      } finally {
        milis += (System.nanoTime() - inicio) / 1_000_000;
      }
    }
  }

  private static final class Totales {
    int fotos;
    int pasan;
    int borradores;
    int conMarca;
    int conModelo;
    int porConfirmar;
  }
}
