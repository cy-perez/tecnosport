package co.tecnosport.api.infrastructure.difusion;

import co.tecnosport.api.application.catalogo.AlmacenDeImagenes;
import co.tecnosport.api.application.difusion.AjustadorDeImagenes;
import co.tecnosport.api.application.difusion.ImagenAPublicar;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Encaja una foto en la proporcion que pide una red, ensanchando el lienzo con {@code ImageIO} y
 * {@code Graphics2D} — los dos del JDK, sin dependencia nueva.
 *
 * <h2>Se anade lienzo, no se corta</h2>
 *
 * <p>El porque esta en {@link AjustadorDeImagenes}: cortar una foto de cuerpo entero para llevarla
 * de 0,62 a 0,8 se lleva el 22 % del alto, que en un pantalon es el ruedo o la pretina.
 *
 * <p><b>El relleno es el color del borde de la propia foto, no blanco.</b> Las del catalogo
 * procesado si tienen fondo blanco, pero las del proveedor llegan como las mando — la muestra que
 * destapo esto tenia #DDDCD8 arriba y blanco abajo. Con blanco fijo, esa foto saldria con dos
 * franjas que no son su fondo. Promediando el borde, las franjas continuan la foto.
 *
 * <h2>El resultado se guarda y se reutiliza</h2>
 *
 * <p>La key sale del contenido y de la proporcion pedida, asi que difundir el mismo producto dos
 * veces no vuelve a subir nada: el objeto ya esta y se sobrescribe con lo mismo. Viven bajo {@code
 * productos/{id}/redes/}, su propio prefijo, para que se distingan de las del catalogo — nadie las
 * ve en el sitio y se van con el producto cuando se borra por prefijo.
 */
public final class AjustadorDeImagenesJava2D implements AjustadorDeImagenes {

  private static final Logger log = LoggerFactory.getLogger(AjustadorDeImagenesJava2D.class);

  /**
   * Cuanto puede alejarse la proporcion de la pedida antes de que valga la pena redibujar. Un pixel
   * arriba o abajo en una foto de 1280 no lo distingue nadie, y redibujar por eso subiria un objeto
   * al bucket en cada difusion.
   */
  private static final double TOLERANCIA = 0.005;

  private static final String TIPO = "image/jpeg";

  private final AlmacenDeImagenes almacen;

  public AjustadorDeImagenesJava2D(AlmacenDeImagenes almacen) {
    this.almacen = Objects.requireNonNull(almacen, "El almacen de imagenes es obligatorio.");
  }

  @Override
  public Optional<ImagenAPublicar> ajustarA(
      UUID productoId, ImagenAPublicar imagen, double proporcionObjetivo) {
    if (proporcionObjetivo <= 0) {
      throw new IllegalArgumentException("La proporcion objetivo tiene que ser mayor que cero.");
    }
    // Cerca no basta: tiene que estar ademas del lado del cuadrado. Una de 1279x1600 (0,7994) esta
    // a
    // menos de la tolerancia de 0,8 y aun asi por debajo de 4:5, y devolverla tal cual era
    // dejarsela al filtro de la red para que la descartara. Paso el 6 de noviembre de 2026.
    if (Math.abs(imagen.proporcion() - proporcionObjetivo) <= TOLERANCIA
        && delLadoDelCuadrado(imagen.proporcion(), proporcionObjetivo)) {
      return Optional.of(imagen);
    }

    Optional<byte[]> original = almacen.objectKeyDe(imagen.url()).flatMap(almacen::leer);
    if (original.isEmpty()) {
      // Pasa de verdad: el catalogo sembrado de local y dev trae imagenes de picsum.photos, que no
      // son de este bucket. Quien llama se queda con la original y deja que la red decida.
      log.info("No se pudo leer {} para ajustarla; se manda como esta.", imagen.url());
      return Optional.empty();
    }

    try {
      BufferedImage fuente = ImageIO.read(new java.io.ByteArrayInputStream(original.get()));
      if (fuente == null) {
        log.warn("Los bytes de {} no son una imagen que ImageIO sepa abrir.", imagen.url());
        return Optional.empty();
      }
      BufferedImage encajada = conLienzoDe(fuente, proporcionObjetivo);
      byte[] bytes = aJpeg(encajada);
      String key = "productos/" + productoId + "/redes/" + sha256(bytes).substring(0, 32) + ".jpg";
      almacen.subir(key, TIPO, bytes);
      return Optional.of(
          new ImagenAPublicar(almacen.urlPublica(key), encajada.getWidth(), encajada.getHeight()));
    } catch (IOException | UncheckedIOException e) {
      log.warn("No se pudo ajustar {}; se manda como esta.", imagen.url(), e);
      return Optional.empty();
    }
  }

  /**
   * La foto centrada en un lienzo de la proporcion pedida. Solo crece: si hace falta mas ancho se
   * ensancha, y si hace falta mas alto se estira hacia arriba y abajo. Nunca se reduce, que seria
   * cortar.
   *
   * <p><b>Se redondea hacia arriba, y con `Math.round` esto no servia para nada.</b> La proporcion
   * que se pide suele ser justo el borde de lo que la red admite — Instagram no acepta nada mas
   * alto que 4:5, asi que a una foto demasiado alta se le pide exactamente 0,8. Con
   * `Math.round(1448 * 0.8)` salen 1158 pixeles de ancho, o sea 0,79972: un pelo <b>por debajo</b>
   * del minimo, y la red la rechaza despues de haberla encajado. Medido contra la cuenta real el 7
   * de octubre de 2026 — el ajustador subio las dos fotos al bucket y el filtro las descarto igual,
   * sin un solo registro que lo explicara.
   *
   * <p><b>Y `ceil` a secas tampoco servia: arreglaba una rama y rompia la otra.</b> Ensanchando,
   * {@code ceil} sube la proporcion; estirando el alto, la baja. Una de 1355x1600 (0,847) encajada
   * a 0,8 salia de 1355x1694 — 0,79988, otra vez por debajo —, y el 6 de noviembre de 2026 el
   * carrusel del bolso baul salio en Instagram con dos fotos de cinco por eso.
   *
   * <p>La regla que vale en las dos ramas es <b>redondear hacia el cuadrado</b>: con la proporcion
   * pedida a un lado de 1 y el resultado entre ella y 1, el resultado cae dentro de cualquier rango
   * que contenga a la pedida y al cuadrado — el de Instagram, de 0,8 a 1,91, los contiene. Y sin
   * que este adaptador tenga que saber cual es ese rango, que es cosa de {@code MetaGraphClient}.
   */
  private static BufferedImage conLienzoDe(BufferedImage fuente, double proporcion) {
    int ancho = fuente.getWidth();
    int alto = fuente.getHeight();
    boolean vertical = proporcion <= 1;
    if ((double) ancho / alto < proporcion) {
      // Ensanchar sube la proporcion: hacia el cuadrado es hacia arriba si la pedida es vertical.
      double exacto = alto * proporcion;
      ancho = (int) (vertical ? Math.ceil(exacto) : Math.max(ancho, Math.floor(exacto)));
    } else {
      // Estirar el alto la baja: hacia el cuadrado es quedarse corto si la pedida es vertical.
      double exacto = ancho / proporcion;
      alto = (int) (vertical ? Math.max(alto, Math.floor(exacto)) : Math.ceil(exacto));
    }

    BufferedImage lienzo = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB);
    Graphics2D pincel = lienzo.createGraphics();
    try {
      pincel.setColor(colorDelBorde(fuente));
      pincel.fillRect(0, 0, ancho, alto);
      pincel.drawImage(
          fuente, (ancho - fuente.getWidth()) / 2, (alto - fuente.getHeight()) / 2, null);
    } finally {
      pincel.dispose();
    }
    return lienzo;
  }

  /** Si {@code real} queda entre la pedida y el cuadrado, o justo en la pedida. */
  private static boolean delLadoDelCuadrado(double real, double pedida) {
    return pedida <= 1 ? real >= pedida : real <= pedida;
  }

  /**
   * El promedio de los cuatro bordes. Se muestrea cada pocos pixeles y no entero: la media de
   * cuarenta puntos por lado es la misma que la de mil, y esto corre por cada foto de cada
   * carrusel.
   */
  static Color colorDelBorde(BufferedImage imagen) {
    int ancho = imagen.getWidth();
    int alto = imagen.getHeight();
    long rojo = 0;
    long verde = 0;
    long azul = 0;
    int cuantos = 0;
    int pasoX = Math.max(1, ancho / 40);
    int pasoY = Math.max(1, alto / 40);
    for (int x = 0; x < ancho; x += pasoX) {
      for (int y : new int[] {0, alto - 1}) {
        int pixel = imagen.getRGB(x, y);
        rojo += (pixel >> 16) & 0xFF;
        verde += (pixel >> 8) & 0xFF;
        azul += pixel & 0xFF;
        cuantos++;
      }
    }
    for (int y = 0; y < alto; y += pasoY) {
      for (int x : new int[] {0, ancho - 1}) {
        int pixel = imagen.getRGB(x, y);
        rojo += (pixel >> 16) & 0xFF;
        verde += (pixel >> 8) & 0xFF;
        azul += pixel & 0xFF;
        cuantos++;
      }
    }
    return new Color((int) (rojo / cuantos), (int) (verde / cuantos), (int) (azul / cuantos));
  }

  private static byte[] aJpeg(BufferedImage imagen) throws IOException {
    ByteArrayOutputStream salida = new ByteArrayOutputStream();
    if (!ImageIO.write(imagen, "jpg", salida)) {
      throw new IOException("Esta JVM no sabe escribir JPEG.");
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
