package co.tecnosport.api.infrastructure.difusion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.application.difusion.ImagenAPublicar;
import co.tecnosport.api.domain.catalogo.Categoria;
import co.tecnosport.api.domain.catalogo.EstadoProducto;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.catalogo.Marca;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.catalogo.Variante;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.Sku;
import co.tecnosport.api.domain.compartido.Slug;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/** La portada de verdad, dibujada en memoria y mirada por píxeles. */
class PortadaJava2DTest {

  private final AlmacenEnMemoria almacen = new AlmacenEnMemoria();
  private final PortadaJava2D portada = new PortadaJava2D(almacen, "https://www.tecnosport.co");

  @Test
  void sale1080Por1350YSeSubeBajoElPrefijoDeRedesDelProducto() throws IOException {
    Producto producto = producto("JBL Grip");
    ImagenAPublicar principal = sembrar(producto, 1183, 1280, Color.WHITE);

    ImagenAPublicar resultado = portada.componer(producto, principal).orElseThrow();

    assertEquals(1080, resultado.ancho());
    assertEquals(1350, resultado.alto());
    String key = almacen.objectKeyDe(resultado.url()).orElseThrow();
    assertTrue(key.startsWith("productos/" + producto.id() + "/redes/portada-"), key);
    BufferedImage subida = ImageIO.read(new ByteArrayInputStream(almacen.objetos.get(key)));
    assertEquals(1080, subida.getWidth());
    assertEquals(1350, subida.getHeight());
  }

  /** La misma portada dos veces es el mismo objeto: difundir otra vez no llena el bucket. */
  @Test
  void laMismaPortadaCaeEnLaMismaKey() {
    Producto producto = producto("JBL Grip");
    ImagenAPublicar principal = sembrar(producto, 1000, 1000, Color.WHITE);

    String primera = portada.componer(producto, principal).orElseThrow().url();
    String segunda = portada.componer(producto, principal).orElseThrow().url();

    assertEquals(primera, segunda);
  }

  /** Una foto que no está en el bucket no tumba la publicación: sale sin portada. */
  @Test
  void unaFotoQueNoEstaEnElBucketDejaLaPortadaVacia() {
    Producto producto = producto("JBL Grip");

    Optional<ImagenAPublicar> resultado =
        portada.componer(producto, new ImagenAPublicar("https://picsum.photos/1200", 1200, 1200));

    assertTrue(resultado.isEmpty());
  }

  /** Un bucket caído no tumba la publicación: el puerto promete vacío, no una excepción. */
  @Test
  void unBucketQueFallaDejaLaPortadaVacia() {
    Producto producto = producto("JBL Grip");
    ImagenAPublicar principal = sembrar(producto, 1000, 1000, Color.WHITE);
    PortadaJava2D conBucketCaido =
        new PortadaJava2D(
            new AlmacenEnMemoria() {
              @Override
              public void subir(String objectKey, String contentType, byte[] bytes) {
                throw new IllegalStateException("GCS no responde");
              }

              @Override
              public Optional<byte[]> leer(String objectKey) {
                return almacen.leer(objectKey);
              }
            },
            "https://www.tecnosport.co");

    assertTrue(conBucketCaido.componer(producto, principal).isEmpty());
  }

  /** La misma portada otra vez no se vuelve a subir: el panel la pide cada vez que se abre. */
  @Test
  void unaPortadaQueYaEstaNoSeVuelveASubir() {
    Producto producto = producto("JBL Grip");
    ImagenAPublicar principal = sembrar(producto, 1000, 1000, Color.WHITE);
    portada.componer(producto, principal).orElseThrow();
    int subidas = almacen.subidas;

    portada.componer(producto, principal).orElseThrow();

    assertEquals(subidas, almacen.subidas);
  }

  @Test
  void unosBytesQueNoSonImagenDejanLaPortadaVacia() {
    Producto producto = producto("JBL Grip");
    String key = "productos/" + producto.id() + "/roto.jpg";
    almacen.objetos.put(key, new byte[] {1, 2, 3});

    assertTrue(
        portada.componer(producto, new ImagenAPublicar(almacen.urlPublica(key), 10, 10)).isEmpty());
  }

  /**
   * Las piezas de marca, cada una en su sitio: la banda grafito, la franja ámbar y los dos
   * chaflanes, que dejan ver lo que hay detrás.
   */
  @Test
  void laBandaLaFranjaYLosChaflanesEstanDondeDiceElManual() {
    Color fondoDeLaFoto = new Color(0xDDDCD8);
    BufferedImage dibujo = portada.dibujar(producto("JBL Grip"), foto(800, 800, fondoDeLaFoto));
    int arribaDeLaBanda = PortadaJava2D.ALTO - 380;

    // Encima de la banda, la foto sobre el color de su borde.
    assertParecido(fondoDeLaFoto, dibujo.getRGB(5, 5));
    // La banda, a la derecha de cualquier texto.
    assertParecido(
        PortadaJava2D.GRAFITO, dibujo.getRGB(PortadaJava2D.ANCHO - 5, arribaDeLaBanda + 5));
    // El chaflán de arriba a la izquierda: esa esquina de la banda deja ver la foto.
    assertParecido(fondoDeLaFoto, dibujo.getRGB(2, arribaDeLaBanda + 2));
    // La franja, al fondo.
    assertParecido(PortadaJava2D.AMBAR, dibujo.getRGB(5, PortadaJava2D.ALTO - 5));
    // Y su chaflán abajo a la derecha: esa esquina es banda, no franja.
    assertParecido(
        PortadaJava2D.GRAFITO, dibujo.getRGB(PortadaJava2D.ANCHO - 2, PortadaJava2D.ALTO - 2));
  }

  /** La foto entera, sin recortar: una prenda alta conserva el ruedo y la pretina. */
  @Test
  void unaFotoAltaCabeEnteraSinRecorte() {
    Color fondo = Color.WHITE;
    BufferedImage alta = foto(620, 1000, fondo);
    Graphics2D pincel = alta.createGraphics();
    pincel.setColor(Color.RED);
    // Una raya en el borde inferior: si se recortara, no aparecería.
    pincel.fillRect(0, 990, 620, 10);
    pincel.dispose();

    BufferedImage dibujo = portada.dibujar(producto("Pantalón"), alta);

    boolean hayRojo = false;
    for (int y = 0; y < PortadaJava2D.ALTO - 380 && !hayRojo; y++) {
      int pixel = dibujo.getRGB(PortadaJava2D.ANCHO / 2, y);
      hayRojo = ((pixel >> 16) & 0xFF) > 200 && ((pixel >> 8) & 0xFF) < 60;
    }
    assertTrue(hayRojo, "El borde inferior de la foto se perdió");
  }

  @Test
  void unNombreLargoSeQuedaEnDosRenglonesConElipsis() {
    FontMetrics metrica = metrica(56f);
    String largo =
        "Celular Samsung Galaxy S25 Ultra 512 GB 12 GB RAM Titanium Silverblue con cargador y"
            + " funda original de regalo";

    List<String> renglones = PortadaJava2D.renglones(largo, metrica, 936, 2);

    assertEquals(2, renglones.size());
    assertTrue(renglones.get(1).endsWith("…"), renglones.toString());
    renglones.forEach(r -> assertTrue(metrica.stringWidth(r) <= 936, r));
    // La elipsis va detrás de una palabra entera, no en la mitad de una.
    String sinElipsis = renglones.get(1).substring(0, renglones.get(1).length() - 1);
    assertTrue(largo.contains(sinElipsis + " "), renglones.toString());
  }

  @Test
  void unNombreCortoNoLlevaElipsis() {
    assertEquals(List.of("JBL Grip"), PortadaJava2D.renglones("JBL Grip", metrica(56f), 936, 2));
  }

  /** Una palabra sola más ancha que el renglón se recorta, en vez de salirse de la banda. */
  @Test
  void unaPalabraImposibleSeRecorta() {
    FontMetrics metrica = metrica(56f);
    List<String> renglones = PortadaJava2D.renglones("W".repeat(80), metrica, 936, 2);

    assertEquals(1, renglones.size());
    assertTrue(metrica.stringWidth(renglones.get(0)) <= 936);
  }

  @Test
  void elSitioSeEscribeSinWww() {
    assertEquals("tecnosport.co", PortadaJava2D.dominioDe("https://www.tecnosport.co"));
    assertEquals("dev.tecnosport.co", PortadaJava2D.dominioDe("https://dev.tecnosport.co/"));
  }

  /**
   * Los colores de la portada son los del kit. Gmail no lee variables CSS y Java2D tampoco, así que
   * van escritos en el código; esto es lo que impide que se queden atrás el día que el kit cambie.
   */
  @Test
  void losColoresSonLosDeTokensJson() throws IOException {
    Path tokens = Path.of("../../../packages/marca/tokens.json");
    String json = Files.readString(tokens).toUpperCase(Locale.ROOT);

    for (Color color :
        List.of(
            PortadaJava2D.GRAFITO,
            PortadaJava2D.AMBAR,
            PortadaJava2D.TEXTO_SOBRE_GRAFITO,
            PortadaJava2D.TEXTO_SUAVE_SOBRE_GRAFITO)) {
      String hex = "#%06X".formatted(color.getRGB() & 0xFFFFFF);
      assertTrue(json.contains("\"" + hex + "\""), hex + " no está en tokens.json");
    }
  }

  // --- armado ---

  private static void assertParecido(Color esperado, int pixel) {
    Color real = new Color(pixel);
    int diferencia =
        Math.abs(esperado.getRed() - real.getRed())
            + Math.abs(esperado.getGreen() - real.getGreen())
            + Math.abs(esperado.getBlue() - real.getBlue());
    // El JPEG de la foto de entrada mueve unos pocos niveles; un color distinto mueve cientos.
    assertTrue(diferencia < 24, "Se esperaba " + esperado + " y hay " + real);
  }

  private static FontMetrics metrica(float tamano) {
    BufferedImage lienzo = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
    Graphics2D pincel = lienzo.createGraphics();
    try {
      return pincel.getFontMetrics(new Font(Font.SANS_SERIF, Font.BOLD, 1).deriveFont(tamano));
    } finally {
      pincel.dispose();
    }
  }

  private ImagenAPublicar sembrar(Producto producto, int ancho, int alto, Color fondo) {
    String key = "productos/" + producto.id() + "/principal.jpg";
    almacen.objetos.put(key, aBytes(foto(ancho, alto, fondo)));
    return new ImagenAPublicar(almacen.urlPublica(key), ancho, alto);
  }

  private static BufferedImage foto(int ancho, int alto, Color fondo) {
    BufferedImage imagen = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB);
    Graphics2D pincel = imagen.createGraphics();
    pincel.setColor(fondo);
    pincel.fillRect(0, 0, ancho, alto);
    pincel.setColor(Color.DARK_GRAY);
    pincel.fillRect(ancho / 4, alto / 4, ancho / 2, alto / 2);
    pincel.dispose();
    return imagen;
  }

  private static byte[] aBytes(BufferedImage imagen) {
    try {
      ByteArrayOutputStream salida = new ByteArrayOutputStream();
      ImageIO.write(imagen, "jpg", salida);
      return salida.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  static Producto producto(String nombre) {
    return new Producto(
        UUID.randomUUID(),
        nombre,
        new Slug("producto-de-prueba"),
        "Un producto.",
        new Marca(UUID.randomUUID(), "JBL"),
        new Categoria(
            UUID.randomUUID(),
            "Parlantes",
            new Slug("parlantes"),
            LineaCatalogo.TECNOLOGIA,
            null,
            List.of()),
        EstadoProducto.PUBLICADO,
        null,
        List.of(),
        List.of(
            Variante.crear(
                new Sku("SKU-1"),
                new Dinero(BigDecimal.valueOf(299900)),
                BigDecimal.ZERO,
                null,
                null,
                List.of())));
  }
}
