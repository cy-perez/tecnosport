package co.tecnosport.api.infrastructure.difusion;

import co.tecnosport.api.application.catalogo.AlmacenDeImagenes;
import co.tecnosport.api.application.difusion.CompositorDePortada;
import co.tecnosport.api.application.difusion.ImagenAPublicar;
import co.tecnosport.api.domain.catalogo.Producto;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.font.TextAttribute;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * La portada de marca que abre el carrusel (ADR-0102), dibujada con {@code Graphics2D} — del JDK,
 * sin dependencia nueva, igual que {@link AjustadorDeImagenesJava2D}.
 *
 * <h2>Cómo es</h2>
 *
 * <p>1080 × 1350, que es 4:5: lo más alto que Instagram admite y lo que más ocupa en el feed.
 * Arriba la foto principal entera, sin recortar, sobre el color de su propio borde —el mismo
 * criterio del ajustador: las del proveedor no llegan en blanco—. Abajo la banda grafito con la
 * categoría, el nombre del producto en Archivo y el pie con el logo y el sitio; y al fondo la
 * franja ámbar, el único acento. El chaflán a 45° va donde el manual lo pone: arriba a la
 * izquierda, en la banda, y abajo a la derecha, en la franja.
 *
 * <p><b>Sin precio</b>: lo razona {@link CompositorDePortada}.
 *
 * <h2>De dónde sale cada cosa</h2>
 *
 * <p>Los colores, de {@code packages/marca/tokens.json} — {@code PortadaJava2DTest} falla si alguno
 * deja de estar allí. Las tipografías y el logo, de {@code resources/difusion/}, que deja {@code
 * packages/marca/generador/fuentes_servidor.py} a partir del kit. Ningún texto es literal: la
 * categoría y el nombre son del producto, y el sitio sale del origen público configurado.
 */
public final class PortadaJava2D implements CompositorDePortada {

  private static final Logger log = LoggerFactory.getLogger(PortadaJava2D.class);

  static final int ANCHO = 1080;
  static final int ALTO = 1350;

  // --- tokens.json ---
  /** {@code color.primario}: el grafito de la banda. */
  static final Color GRAFITO = new Color(0x1B1F26);

  /** {@code color.acento}: la franja, y nada más. */
  static final Color AMBAR = new Color(0xF5B301);

  /** {@code modo_oscuro.texto}: el nombre del producto sobre el grafito. */
  static final Color TEXTO_SOBRE_GRAFITO = new Color(0xEDF0F4);

  /** {@code modo_oscuro.texto-suave}: la categoría y el sitio. */
  static final Color TEXTO_SUAVE_SOBRE_GRAFITO = new Color(0x98A2B3);

  // --- medidas, en píxeles de la portada ---
  private static final int MARGEN = 72;
  private static final int ALTO_BANDA = 380;
  private static final int ALTO_FRANJA = 20;
  private static final int CHAFLAN = 48;
  private static final int MARGEN_FOTO = 64;
  private static final int ALTO_LOGO = 40;
  private static final float TAMANO_NOMBRE = 56f;
  private static final float TAMANO_CATEGORIA = 24f;
  private static final float TAMANO_SITIO = 24f;
  private static final int MAXIMO_LINEAS_NOMBRE = 2;
  private static final String ELIPSIS = "…";

  /**
   * Calidad del JPEG: el texto se nota borroso por debajo de 0,9, y la portada es casi todo texto.
   */
  private static final float CALIDAD_JPEG = 0.92f;

  private static final String TIPO = "image/jpeg";

  private static final Font RESPALDO = new Font(Font.SANS_SERIF, Font.BOLD, 1);

  private final AlmacenDeImagenes almacen;
  private final String sitio;
  private final Font display;
  private final Font texto;
  private final Font textoMedio;
  private final BufferedImage logo;

  /**
   * @param urlBaseDelSitio el origen público; en la banda se escribe solo el dominio, sin {@code
   *     www.}, que es como se dice en voz alta.
   */
  public PortadaJava2D(AlmacenDeImagenes almacen, String urlBaseDelSitio) {
    this.almacen = Objects.requireNonNull(almacen, "El almacén de imágenes es obligatorio.");
    this.sitio = dominioDe(Objects.requireNonNull(urlBaseDelSitio, "La URL del sitio lo es."));
    // Se cargan al construir y no al dibujar: si una tipografía falta en el jar, el servicio no
    // levanta, en vez de descubrirse el día que alguien pulsa «Difundir».
    this.display = fuente("difusion/fuentes/archivo-700.ttf");
    this.texto = fuente("difusion/fuentes/ibmplexsans-400.ttf");
    this.textoMedio = fuente("difusion/fuentes/ibmplexsans-500.ttf");
    this.logo = imagen("difusion/logo-mono-negativo.png");
  }

  @Override
  public Optional<ImagenAPublicar> componer(Producto producto, ImagenAPublicar principal) {
    Objects.requireNonNull(producto, "El producto no puede ser nulo.");
    Objects.requireNonNull(principal, "La foto principal no puede ser nula.");

    // Todo dentro del try y atrapando RuntimeException, no solo IOException: el bucket falla con
    // una excepción propia sin comprobar, e ImageIO con IllegalArgumentException ante una imagen
    // rara. El contrato del puerto es «vacío si no se pudo», y una portada que falla no puede
    // tumbar la publicación ni dar un 500 en el panel.
    try {
      Optional<byte[]> original = almacen.objectKeyDe(principal.url()).flatMap(almacen::leer);
      if (original.isEmpty()) {
        // El catálogo sembrado de local y dev trae fotos de picsum.photos, que no son del bucket.
        log.info("No se pudo leer {} para la portada; se publica sin ella.", principal.url());
        return Optional.empty();
      }
      BufferedImage foto = ImageIO.read(new ByteArrayInputStream(original.get()));
      if (foto == null) {
        log.warn("Los bytes de {} no son una imagen que ImageIO sepa abrir.", principal.url());
        return Optional.empty();
      }
      byte[] bytes = aJpeg(dibujar(producto, foto));
      String key =
          "productos/"
              + producto.id()
              + "/redes/portada-"
              + sha256(bytes).substring(0, 32)
              + ".jpg";
      // La llave sale del contenido: si ya está, es esta misma portada y no se vuelve a subir.
      // El panel la pide cada vez que se abre la ficha, y el bucket guarda versiones.
      if (almacen.tamanoBytes(key).isEmpty()) {
        almacen.subir(key, TIPO, bytes);
      }
      return Optional.of(new ImagenAPublicar(almacen.urlPublica(key), ANCHO, ALTO));
    } catch (IOException | RuntimeException e) {
      log.warn("No se pudo componer la portada de {}; se publica sin ella.", producto.id(), e);
      return Optional.empty();
    }
  }

  /** Visible para las pruebas, que miran los píxeles sin pasar por el bucket. */
  BufferedImage dibujar(Producto producto, BufferedImage foto) {
    BufferedImage lienzo = new BufferedImage(ANCHO, ALTO, BufferedImage.TYPE_INT_RGB);
    Graphics2D pincel = lienzo.createGraphics();
    try {
      conCalidad(pincel);
      int arribaDeLaBanda = ALTO - ALTO_BANDA;

      // La foto, contenida y centrada, sobre el color de su propio borde. El fondo cubre el lienzo
      // entero y no solo lo de encima de la banda: el chaflán de la banda lo deja ver.
      pincel.setColor(AjustadorDeImagenesJava2D.colorDelBorde(foto));
      pincel.fillRect(0, 0, ANCHO, ALTO);
      dibujarFoto(pincel, foto, arribaDeLaBanda);

      // La banda, con el chaflán arriba a la izquierda.
      pincel.setColor(GRAFITO);
      pincel.fillPolygon(
          new Polygon(
              new int[] {0, CHAFLAN, ANCHO, ANCHO, 0},
              new int[] {arribaDeLaBanda + CHAFLAN, arribaDeLaBanda, arribaDeLaBanda, ALTO, ALTO},
              5));

      // La franja, con el chaflán abajo a la derecha.
      int arribaDeLaFranja = ALTO - ALTO_FRANJA;
      pincel.setColor(AMBAR);
      pincel.fillPolygon(
          new Polygon(
              new int[] {0, ANCHO, ANCHO - ALTO_FRANJA, 0},
              new int[] {arribaDeLaFranja, arribaDeLaFranja, ALTO, ALTO},
              4));

      dibujarTextos(pincel, producto, arribaDeLaBanda, arribaDeLaFranja);
    } finally {
      pincel.dispose();
    }
    return lienzo;
  }

  private static void dibujarFoto(Graphics2D pincel, BufferedImage foto, int altoDisponible) {
    int anchoCaja = ANCHO - 2 * MARGEN_FOTO;
    int altoCaja = altoDisponible - 2 * MARGEN_FOTO;
    double escala =
        Math.min((double) anchoCaja / foto.getWidth(), (double) altoCaja / foto.getHeight());
    int ancho = (int) Math.round(foto.getWidth() * escala);
    int alto = (int) Math.round(foto.getHeight() * escala);
    pincel.drawImage(
        foto, (ANCHO - ancho) / 2, MARGEN_FOTO + (altoCaja - alto) / 2, ancho, alto, null);
  }

  private void dibujarTextos(
      Graphics2D pincel, Producto producto, int arribaDeLaBanda, int arribaDeLaFranja) {
    int anchoUtil = ANCHO - 2 * MARGEN;

    // La categoría, en versalitas espaciadas: dice qué es antes de decir cuál.
    Font categoria =
        textoMedio.deriveFont(
            Map.of(TextAttribute.SIZE, TAMANO_CATEGORIA, TextAttribute.TRACKING, 0.12f));
    pincel.setFont(categoria);
    pincel.setColor(TEXTO_SUAVE_SOBRE_GRAFITO);
    FontMetrics metricaCategoria = pincel.getFontMetrics();
    int linea = arribaDeLaBanda + 64 + metricaCategoria.getAscent();
    pincel.drawString(
        recortar(
            producto.categoria().nombre().toUpperCase(Locale.of("es")),
            metricaCategoria,
            anchoUtil),
        MARGEN,
        linea);

    // El nombre, hasta dos líneas. Si trae una letra que Archivo no tiene —el catálogo lo escriben
    // proveedores—, se usa la sans del sistema en vez de pintar cuadros.
    Font nombre = display.canDisplayUpTo(producto.nombre()) == -1 ? display : RESPALDO;
    pincel.setFont(nombre.deriveFont(TAMANO_NOMBRE));
    pincel.setColor(TEXTO_SOBRE_GRAFITO);
    FontMetrics metricaNombre = pincel.getFontMetrics();
    linea += 20;
    for (String renglon :
        renglones(producto.nombre(), metricaNombre, anchoUtil, MAXIMO_LINEAS_NOMBRE)) {
      linea += metricaNombre.getAscent();
      pincel.drawString(renglon, MARGEN, linea);
      linea += metricaNombre.getDescent();
    }

    // El pie de la banda: el logo a la izquierda y el sitio a la derecha, sobre la misma línea.
    int abajoDelPie = arribaDeLaFranja - 52;
    int anchoLogo = (int) Math.round((double) logo.getWidth() * ALTO_LOGO / logo.getHeight());
    pincel.drawImage(logo, MARGEN, abajoDelPie - ALTO_LOGO, anchoLogo, ALTO_LOGO, null);

    pincel.setFont(texto.deriveFont(TAMANO_SITIO));
    pincel.setColor(TEXTO_SUAVE_SOBRE_GRAFITO);
    FontMetrics metricaSitio = pincel.getFontMetrics();
    pincel.drawString(
        sitio,
        ANCHO - MARGEN - metricaSitio.stringWidth(sitio),
        abajoDelPie - (ALTO_LOGO - metricaSitio.getAscent()) / 2);
  }

  /**
   * El texto partido en palabras hasta {@code maximo} renglones; si sobra, el último acaba en
   * elipsis. Una palabra sola más ancha que el renglón se recorta también, en vez de salirse.
   */
  static List<String> renglones(String texto, FontMetrics metrica, int ancho, int maximo) {
    List<String> renglones = new ArrayList<>();
    String actual = "";
    String[] palabras = texto.strip().split("\\s+");
    int i = 0;
    while (i < palabras.length && renglones.size() < maximo) {
      String candidato = actual.isEmpty() ? palabras[i] : actual + " " + palabras[i];
      if (metrica.stringWidth(candidato) <= ancho || actual.isEmpty()) {
        actual = candidato;
        i++;
      } else {
        renglones.add(actual);
        actual = "";
      }
    }
    if (!actual.isEmpty() && renglones.size() < maximo) {
      renglones.add(actual);
    }
    if (i < palabras.length) {
      int ultimo = renglones.size() - 1;
      renglones.set(ultimo, conElipsis(renglones.get(ultimo), metrica, ancho));
    }
    return renglones.stream().map(r -> recortar(r, metrica, ancho)).toList();
  }

  /** El texto tal cual si cabe; si no, cortado por letras y con elipsis al final. */
  static String recortar(String texto, FontMetrics metrica, int ancho) {
    return metrica.stringWidth(texto) <= ancho ? texto : conElipsis(texto, metrica, ancho);
  }

  /**
   * El texto con elipsis al final. Se quitan palabras enteras mientras quede más de una, y letras
   * solo si una palabra sola no cabe: «Midnight Black…» se lee, «Midnight Black co…» no.
   */
  private static String conElipsis(String texto, FontMetrics metrica, int ancho) {
    String base = texto.strip();
    while (base.contains(" ") && metrica.stringWidth(base + ELIPSIS) > ancho) {
      base = base.substring(0, base.lastIndexOf(' ')).strip();
    }
    while (!base.isEmpty() && metrica.stringWidth(base + ELIPSIS) > ancho) {
      base = base.substring(0, base.length() - 1).strip();
    }
    return base + ELIPSIS;
  }

  private static void conCalidad(Graphics2D pincel) {
    pincel.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    pincel.setRenderingHint(
        RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    pincel.setRenderingHint(
        RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
    pincel.setRenderingHint(
        RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
    pincel.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
  }

  static String dominioDe(String urlBaseDelSitio) {
    String host = URI.create(urlBaseDelSitio.strip()).getHost();
    if (host == null) {
      throw new IllegalArgumentException("La URL del sitio no tiene dominio: " + urlBaseDelSitio);
    }
    return host.startsWith("www.") ? host.substring(4) : host;
  }

  private static Font fuente(String recurso) {
    try (InputStream entrada = recurso(recurso)) {
      return Font.createFont(Font.TRUETYPE_FONT, entrada);
    } catch (IOException | FontFormatException e) {
      throw new IllegalStateException("No se pudo cargar la tipografía " + recurso + ".", e);
    }
  }

  private static BufferedImage imagen(String recurso) {
    try (InputStream entrada = recurso(recurso)) {
      BufferedImage leida = ImageIO.read(entrada);
      if (leida == null) {
        throw new IllegalStateException(recurso + " no es una imagen que ImageIO sepa abrir.");
      }
      return leida;
    } catch (IOException e) {
      throw new IllegalStateException("No se pudo cargar " + recurso + ".", e);
    }
  }

  private static InputStream recurso(String recurso) {
    InputStream entrada = PortadaJava2D.class.getClassLoader().getResourceAsStream(recurso);
    if (entrada == null) {
      throw new IllegalStateException(
          "Falta " + recurso + " en el jar. Lo deja packages/marca/generador/fuentes_servidor.py.");
    }
    return entrada;
  }

  private static byte[] aJpeg(BufferedImage imagen) throws IOException {
    ImageWriter escritor = ImageIO.getImageWritersByFormatName("jpg").next();
    ByteArrayOutputStream salida = new ByteArrayOutputStream();
    try (MemoryCacheImageOutputStream flujo = new MemoryCacheImageOutputStream(salida)) {
      escritor.setOutput(flujo);
      ImageWriteParam parametros = escritor.getDefaultWriteParam();
      parametros.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
      parametros.setCompressionQuality(CALIDAD_JPEG);
      escritor.write(null, new IIOImage(imagen, null, null), parametros);
    } finally {
      escritor.dispose();
    }
    return salida.toByteArray();
  }

  private static String sha256(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("La JVM no ofrece SHA-256.", e);
    }
  }
}
