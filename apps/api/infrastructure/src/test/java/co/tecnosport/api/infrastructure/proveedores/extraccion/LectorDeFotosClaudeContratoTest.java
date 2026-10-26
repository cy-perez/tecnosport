package co.tecnosport.api.infrastructure.proveedores.extraccion;

import static org.assertj.core.api.Assertions.assertThat;

import co.tecnosport.api.application.proveedores.FotosParaLeer;
import co.tecnosport.api.application.proveedores.FotosParaLeer.FotoParaLeer;
import co.tecnosport.api.application.proveedores.FotosParaLeer.ProductoNombrado;
import co.tecnosport.api.application.proveedores.ResultadoExtraccion;
import co.tecnosport.api.application.proveedores.TextoDePublicacion;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.proveedores.LecturaDeFotos;
import co.tecnosport.api.domain.proveedores.PrecioAdicional;
import co.tecnosport.api.domain.proveedores.ProductoExtraido;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Contra la API real, como {@link ExtractorClaudeContratoTest}: <b>no corre en el pipeline</b>
 * (etiqueta {@code integracion-externa} y {@code ANTHROPIC_API_KEY}). Lo que compra es saber que la
 * API acepta el esquema del lector y las fotos en base64, y que los textos de La Riverah y Violeta
 * del 9 de octubre de 2026 salen con sus campos nuevos. Las fotos se dibujan aquí: las del
 * proveedor no se copian al repositorio.
 */
@Tag("integracion-externa")
@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
class LectorDeFotosClaudeContratoTest {

  private static ExtractorClaude extractor() {
    return new ExtractorClaude(
        URI.create("https://api.anthropic.com"),
        System.getenv("ANTHROPIC_API_KEY"),
        "claude-haiku-4-5-20251001",
        2048,
        Duration.ofSeconds(30),
        2,
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
        Duration.ofSeconds(90),
        2,
        Duration.ofSeconds(2),
        RecursosDelExtractor.promptDelLector(),
        RecursosDelExtractor.esquemaDelLector());
  }

  /**
   * El lector de calzado (ADR-0090), con el modelo y el esfuerzo que trae {@code application.yml}
   * por omisión. Cambiarlos allá obliga a cambiarlos aquí.
   */
  private static LectorDeFotosClaude lectorDeCalzado() {
    return new LectorDeFotosClaude(
        URI.create("https://api.anthropic.com"),
        System.getenv("ANTHROPIC_API_KEY"),
        "claude-sonnet-5-5",
        16000,
        Duration.ofSeconds(150),
        2,
        Duration.ofSeconds(2),
        RecursosDelExtractor.promptDelLector(),
        RecursosDelExtractor.esquemaDelLector(),
        "low");
  }

  /** Una prenda de un color con un rótulo y, si se pide, un pie impreso como los de La Riverah. */
  private static byte[] foto(Color prenda, String rotulo, String pie) throws IOException {
    BufferedImage imagen = new BufferedImage(800, 1000, BufferedImage.TYPE_INT_RGB);
    Graphics2D lienzo = imagen.createGraphics();
    lienzo.setColor(new Color(0xE8, 0xE2, 0xD8));
    lienzo.fillRect(0, 0, 800, 1000);
    lienzo.setColor(prenda);
    lienzo.fillRect(200, 150, 400, 550);
    lienzo.fillRect(100, 150, 120, 220);
    lienzo.fillRect(580, 150, 120, 220);
    lienzo.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 36));
    if (rotulo != null) {
      lienzo.setColor(Color.WHITE);
      lienzo.fillRect(40, 760, 420, 60);
      lienzo.setColor(Color.BLACK);
      lienzo.drawString(rotulo, 50, 802);
    }
    if (pie != null) {
      lienzo.setColor(Color.WHITE);
      lienzo.fillRect(0, 860, 800, 140);
      lienzo.setColor(Color.BLACK);
      String[] lineas = pie.split("\n");
      for (int i = 0; i < lineas.length; i++) {
        lienzo.drawString(lineas[i], 20, 910 + i * 44);
      }
    }
    lienzo.dispose();
    ByteArrayOutputStream salida = new ByteArrayOutputStream();
    ImageIO.write(imagen, "jpg", salida);
    return salida.toByteArray();
  }

  @Test
  void laApiAceptaElEsquemaDelLectorYLeeRotulosYPies() throws IOException {
    LecturaDeFotos lectura =
        lector()
            .leer(
                new FotosParaLeer(
                    "Camiseta Slim(261002)\n💲42\nTalla S M L\n\nJogger. (VY3026)\n💲72",
                    List.of(
                        new ProductoNombrado("Camiseta slim", "261002"),
                        new ProductoNombrado("Jogger", "VY3026")),
                    List.of(
                        new FotoParaLeer(0, foto(Color.BLACK, "C:261002 J:VY3026", null)),
                        new FotoParaLeer(
                            1, foto(Color.WHITE, null, "Tallas: S, M\nSKU: RV102347")))))
            .orElseThrow();

    assertThat(lectura.deLaFoto(0).orElseThrow().codigos()).contains("261002", "VY3026");
    assertThat(lectura.deLaFoto(1).orElseThrow().sku()).isEqualTo("RV102347");
    assertThat(lectura.deLaFoto(1).orElseThrow().tallasDelPie()).containsExactly("S", "M");
  }

  /**
   * Imperio Wicho, 10 de octubre de 2026: «Tallas disponibles en la foto!», con un recuadro blanco
   * de tallas una debajo de otra en la esquina de arriba (ADR-0085). La API acepta el campo nuevo y
   * el lector lo llena.
   */
  @Test
  void leeLasTallasDelRotuloDeLaEsquina() throws IOException {
    BufferedImage imagen = new BufferedImage(900, 1200, BufferedImage.TYPE_INT_RGB);
    Graphics2D lienzo = imagen.createGraphics();
    lienzo.setColor(new Color(0xB0, 0x7A, 0x50));
    lienzo.fillRect(0, 0, 900, 1200);
    lienzo.setColor(Color.BLACK);
    lienzo.fillRoundRect(150, 650, 560, 220, 80, 80);
    lienzo.setColor(Color.WHITE);
    lienzo.fillRect(150, 820, 560, 50);
    lienzo.fillRoundRect(780, 200, 80, 190, 16, 16);
    lienzo.setColor(Color.BLACK);
    lienzo.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 30));
    String[] tallas = {"35", "36", "38", "41", "42"};
    for (int i = 0; i < tallas.length; i++) {
      lienzo.drawString(tallas[i], 800, 235 + i * 34);
    }
    lienzo.dispose();
    ByteArrayOutputStream salida = new ByteArrayOutputStream();
    ImageIO.write(imagen, "jpg", salida);

    LecturaDeFotos lectura =
        lector()
            .leer(
                new FotosParaLeer(
                    "NIKE UNISEX ULTRALIVIANO🔥\nSUELA IMPORTADA💯\nTallas disponibles en la foto!\n"
                        + "Horma normal Dama, Aumenta 1 Cab⚠️⚠️\n💰*$85,000*",
                    List.of(new ProductoNombrado("Tenis estilo Nike unisex", null)),
                    List.of(new FotoParaLeer(0, salida.toByteArray()))))
            .orElseThrow();

    assertThat(lectura.deLaFoto(0).orElseThrow().tallasImpresas())
        .containsExactly("35", "36", "38", "41", "42");
    assertThat(lectura.deLaFoto(0).orElseThrow().tallasDelPie()).isEmpty();
  }

  /**
   * Lo que compra: que la API acepte el esquema con la marca y el grupo del tenis, el esfuerzo sin
   * temperatura, y que la respuesta con razonamiento se lea. Dos fotos de la misma silueta en dos
   * colores: el lector las tiene que poner en el mismo tenis.
   */
  @Test
  void laApiAceptaElLectorDeCalzadoYJuntaElMismoTenis() throws IOException {
    LecturaDeFotos lectura =
        lectorDeCalzado()
            .leer(
                new FotosParaLeer(
                    "Detal :$180.000\nX mayor 💰135",
                    List.of(new ProductoNombrado(null, null)),
                    List.of(
                        new FotoParaLeer(0, tenis(Color.BLACK, Color.RED)),
                        new FotoParaLeer(1, tenis(Color.LIGHT_GRAY, Color.BLACK)))))
            .orElseThrow();

    assertThat(lectura.fotos()).hasSize(2);
    assertThat(lectura.deLaFoto(1).flatMap(f -> f.calzadoOpcional()))
        .hasValueSatisfying(c -> assertThat(c.mismoTenisQueOpcional()).contains(0));
  }

  /** Un tenis dibujado: la capellada de un color y la suela de otro, sobre un fondo de madera. */
  private static byte[] tenis(Color capellada, Color suela) throws IOException {
    BufferedImage imagen = new BufferedImage(1000, 800, BufferedImage.TYPE_INT_RGB);
    Graphics2D lienzo = imagen.createGraphics();
    lienzo.setColor(new Color(0xD8, 0xC3, 0xA5));
    lienzo.fillRect(0, 0, 1000, 800);
    lienzo.setColor(suela);
    lienzo.fillRoundRect(120, 500, 760, 90, 60, 60);
    lienzo.setColor(capellada);
    lienzo.fillPolygon(
        new int[] {140, 300, 560, 860, 860, 140}, new int[] {500, 300, 260, 420, 500, 500}, 6);
    lienzo.setColor(Color.WHITE);
    for (int i = 0; i < 5; i++) {
      lienzo.fillRect(380 + i * 40, 300 + i * 6, 24, 8);
    }
    lienzo.dispose();
    ByteArrayOutputStream salida = new ByteArrayOutputStream();
    ImageIO.write(imagen, "jpg", salida);
    return salida.toByteArray();
  }

  /** La Riverah, 17:36 del 9 de octubre de 2026: la gorra y el combo no son otro producto. */
  @Test
  void laGorraYElComboVanEnPreciosAdicionales() {
    ResultadoExtraccion resultado =
        extractor()
            .extraer(
                new TextoDePublicacion(
                    "*NUEVA COLECCIÓN* 🏃🏃\n\nCAMISETA POLO 1.1 👦👦\n⭕ tipo polo\n⭕Calidad 1.1\n"
                        + "⭕M L Xl 2XL\n\n  🤑  $55.000🥳\nPromo 4x200.000\n\nGorra 🧢 $35.000\n"
                        + "Polo+ gorra $ 85.000",
                    List.of(),
                    LineaCatalogo.ROPA));

    assertThat(resultado.productos()).hasSize(1);
    ProductoExtraido polo = resultado.productos().getFirst();
    assertThat(polo.precioProveedor()).isEqualTo(Dinero.deCop(55000));
    assertThat(polo.preciosAdicionales())
        .extracting(PrecioAdicional::precio)
        .contains(Dinero.deCop(35000), Dinero.deCop(85000), Dinero.deCop(200000));
  }

  /** Violeta, 11:32 del 9 de octubre de 2026. */
  @Test
  void lasTallasDeCadaTonoDeLaBlusa() {
    ResultadoExtraccion resultado =
        extractor()
            .extraer(
                new TextoDePublicacion(
                    "NEW COLLECTION!✨✨\n\nBlusa Licrada Herraje trasero(VY2945)\n💲38\n"
                        + "Talla SM ML(negro)\nTalla ML(cocoa)\nTalla SM(verde)",
                    List.of(),
                    LineaCatalogo.ROPA));

    ProductoExtraido blusa = resultado.productos().getFirst();
    assertThat(blusa.tallasPorTono().tallasDe("cocoa")).contains(List.of("ML"));
    assertThat(blusa.tallasPorTono().tallasDe("verde")).contains(List.of("SM"));
  }
}
