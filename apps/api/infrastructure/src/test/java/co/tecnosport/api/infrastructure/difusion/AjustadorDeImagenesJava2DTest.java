package co.tecnosport.api.infrastructure.difusion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.application.catalogo.AlmacenDeImagenes;
import co.tecnosport.api.application.catalogo.UrlFirmada;
import co.tecnosport.api.application.difusion.ImagenAPublicar;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/** El encaje de verdad, contra imágenes reales en memoria. */
class AjustadorDeImagenesJava2DTest {

  private static final UUID PRODUCTO = UUID.randomUUID();

  private final AlmacenEnMemoria almacen = new AlmacenEnMemoria();
  private final AjustadorDeImagenesJava2D ajustador = new AjustadorDeImagenesJava2D(almacen);

  /**
   * El caso que destapó esto: una foto de proveedor de 791x1280 (0,62), demasiado alta para
   * Instagram. Encajada a 0,8 sale más ancha y <b>con el mismo alto</b>: no se cortó nada.
   */
  @Test
  void unaFotoDemasiadoAltaSeEnsanchaSinPerderAlto() {
    ImagenAPublicar original = sembrar("alta.jpg", 791, 1280, Color.WHITE);

    ImagenAPublicar encajada = ajustador.ajustarA(PRODUCTO, original, 0.8).orElseThrow();

    assertEquals(1280, encajada.alto(), "el alto no se toca: cortarlo sería cortar la prenda");
    assertEquals(1024, encajada.ancho());
    assertEquals(0.8, encajada.proporcion(), 0.001);
  }

  /** Y al revés: una apaisada de más crece de alto, tampoco se recorta. */
  @Test
  void unaFotoDemasiadoAnchaCreceDeAlto() {
    ImagenAPublicar original = sembrar("ancha.jpg", 2400, 1000, Color.WHITE);

    ImagenAPublicar encajada = ajustador.ajustarA(PRODUCTO, original, 1.91).orElseThrow();

    assertEquals(2400, encajada.ancho());
    assertEquals(1257, encajada.alto());
  }

  /** La que ya está en la proporción pedida se devuelve tal cual, sin tocar el bucket. */
  @Test
  void laQueYaEncajaNoSeVuelveASubir() {
    ImagenAPublicar original = sembrar("cuadrada.jpg", 1000, 1000, Color.WHITE);

    ImagenAPublicar encajada = ajustador.ajustarA(PRODUCTO, original, 1.0).orElseThrow();

    assertEquals(original, encajada);
    assertEquals(1, almacen.objetos.size(), "solo la sembrada");
  }

  /**
   * Las franjas llevan el color del borde de la foto, no blanco fijo. Las del proveedor llegan como
   * las mandó: la muestra que destapó esto tenía el borde en gris claro, y con blanco fijo habría
   * salido con dos franjas que no son su fondo.
   */
  @Test
  void lasFranjasTomanElColorDelBordeYNoBlanco() throws IOException {
    ImagenAPublicar original = sembrar("gris.jpg", 600, 1200, new Color(0xDD, 0xDC, 0xD8));

    ImagenAPublicar encajada = ajustador.ajustarA(PRODUCTO, original, 0.8).orElseThrow();

    BufferedImage pintada = leerDelAlmacen(encajada.url());
    int franja = pintada.getRGB(2, pintada.getHeight() / 2);
    assertEquals(0xDD, (franja >> 16) & 0xFF, 2);
    assertEquals(0xD8, franja & 0xFF, 2);
  }

  /** Dos encajes de la misma foto a la misma proporción dan la misma key: no se acumulan copias. */
  @Test
  void encajarDosVecesNoDuplicaObjetos() {
    ImagenAPublicar original = sembrar("alta.jpg", 791, 1280, Color.WHITE);

    String una = ajustador.ajustarA(PRODUCTO, original, 0.8).orElseThrow().url();
    String otra = ajustador.ajustarA(PRODUCTO, original, 0.8).orElseThrow().url();

    assertEquals(una, otra);
    assertEquals(2, almacen.objetos.size(), "la sembrada y una sola encajada");
  }

  /** Y a proporciones distintas, keys distintas: si no, la segunda leería la primera. */
  @Test
  void proporcionesDistintasDanObjetosDistintos() {
    ImagenAPublicar original = sembrar("alta.jpg", 791, 1280, Color.WHITE);

    String aCuatroQuintos = ajustador.ajustarA(PRODUCTO, original, 0.8).orElseThrow().url();
    String aCuadrada = ajustador.ajustarA(PRODUCTO, original, 1.0).orElseThrow().url();

    assertNotEquals(aCuatroQuintos, aCuadrada);
  }

  /**
   * Una URL que no es de este bucket no se puede encajar, y no es un caso raro: el catálogo
   * sembrado de local y dev trae imágenes de picsum.photos. Devuelve vacío y quien llama se queda
   * con la original.
   */
  @Test
  void loQueNoEstaEnElBucketNoSePuedeEncajar() {
    ImagenAPublicar ajena = new ImagenAPublicar("https://picsum.photos/800/1200", 800, 1200);

    assertTrue(ajustador.ajustarA(PRODUCTO, ajena, 0.8).isEmpty());
  }

  /** Unos bytes que no son una imagen tampoco revientan: vacío y la original sale como está. */
  @Test
  void unosBytesQueNoSonImagenNoRevientan() {
    almacen.objetos.put("productos/x/rota.jpg", "no soy un jpeg".getBytes());
    ImagenAPublicar rota =
        new ImagenAPublicar(almacen.urlPublica("productos/x/rota.jpg"), 800, 1200);

    assertTrue(ajustador.ajustarA(PRODUCTO, rota, 0.8).isEmpty());
  }

  /** La foto encajada se guarda bajo el prefijo del producto: se va con él cuando se borra. */
  @Test
  void laEncajadaViveBajoElPrefijoDelProducto() {
    ImagenAPublicar original = sembrar("alta.jpg", 791, 1280, Color.WHITE);

    String url = ajustador.ajustarA(PRODUCTO, original, 0.8).orElseThrow().url();

    assertTrue(url.contains("productos/" + PRODUCTO + "/redes/"), url);
  }

  // --- armado ---

  private ImagenAPublicar sembrar(String nombre, int ancho, int alto, Color fondo) {
    BufferedImage imagen = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB);
    Graphics2D pincel = imagen.createGraphics();
    pincel.setColor(fondo);
    pincel.fillRect(0, 0, ancho, alto);
    pincel.dispose();
    String key = "productos/" + PRODUCTO + "/" + nombre;
    almacen.objetos.put(key, aBytes(imagen));
    return new ImagenAPublicar(almacen.urlPublica(key), ancho, alto);
  }

  private BufferedImage leerDelAlmacen(String url) throws IOException {
    byte[] bytes = almacen.objetos.get(almacen.objectKeyDe(url).orElseThrow());
    return ImageIO.read(new ByteArrayInputStream(bytes));
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

  /** Doble escrito a mano: un mapa de key a bytes y la URL pública delante. */
  private static final class AlmacenEnMemoria implements AlmacenDeImagenes {
    private static final String BASE = "https://publico.local/";
    private final Map<String, byte[]> objetos = new HashMap<>();

    @Override
    public void subir(String objectKey, String contentType, byte[] bytes) {
      objetos.put(objectKey, bytes);
    }

    @Override
    public Optional<byte[]> leer(String objectKey) {
      return Optional.ofNullable(objetos.get(objectKey));
    }

    @Override
    public String urlPublica(String objectKey) {
      return BASE + objectKey;
    }

    @Override
    public Optional<String> objectKeyDe(String urlPublica) {
      return urlPublica.startsWith(BASE)
          ? Optional.of(urlPublica.substring(BASE.length()))
          : Optional.empty();
    }

    @Override
    public UrlFirmada generarUrlDeSubida(String objectKey, String contentType) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Optional<Long> tamanoBytes(String objectKey) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean eliminar(String objectKey) {
      throw new UnsupportedOperationException();
    }

    @Override
    public int eliminarPorPrefijo(String prefijo, Set<String> conservar) {
      throw new UnsupportedOperationException();
    }
  }
}
