package co.tecnosport.api.application.catalogo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.domain.catalogo.Categoria;
import co.tecnosport.api.domain.catalogo.ImagenProducto;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.catalogo.Marca;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.catalogo.TipoImagen;
import co.tecnosport.api.domain.catalogo.VarianteDeImagen;
import co.tecnosport.api.domain.compartido.HashContenido;
import co.tecnosport.api.domain.compartido.Slug;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ConfirmarImagenPrincipalTest {

  /** El SHA-256 que el panel calcula en el navegador sobre los bytes que subió. */
  private static final String HASH =
      "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

  private final RepositorioProductosFalso repositorioProductos = new RepositorioProductosFalso();
  private final AlmacenDeImagenesFalso almacenDeImagenes = new AlmacenDeImagenesFalso();
  private final ConfirmarImagenPrincipal confirmarImagenPrincipal =
      new ConfirmarImagenPrincipal(repositorioProductos, almacenDeImagenes);

  private Producto productoDePrueba() {
    Marca marca = Marca.crear("TecnoSport");
    Categoria categoria =
        Categoria.crear("Morrales", new Slug("bolsos-dama-morrales"), LineaCatalogo.BOLSOS);
    return Producto.crear("Morral urbano", new Slug("morral-urbano"), "", marca, categoria);
  }

  @Test
  void confirmaLaImagenYReemplazaLaPrincipalDelProducto() {
    Producto producto = productoDePrueba();
    repositorioProductos.conProductos(producto);
    String objectKey = "productos/" + producto.id() + "/principal-abc.webp";
    almacenDeImagenes.conObjeto(objectKey, 45_000);

    var imagen =
        confirmarImagenPrincipal
            .ejecutar(
                new ConfirmarImagenPrincipalComando(
                    producto.id(),
                    List.of(new VarianteSubida(1000, objectKey)),
                    null,
                    800,
                    HASH,
                    "alt es",
                    "alt en"))
            .imagen();

    assertEquals(TipoImagen.PRINCIPAL, imagen.tipo());
    assertEquals(1000, imagen.ancho());
    assertEquals(800, imagen.alto());
    assertEquals(45_000, imagen.bytes());
    assertEquals(HASH, imagen.hash().valor());
    assertEquals(producto.id(), repositorioProductos.ultimoProductoIdConImagen);
    assertEquals(imagen, repositorioProductos.ultimaImagenPrincipal);
  }

  @Test
  void productoInexistenteLanzaProductoNoEncontradoPorId() {
    UUID productoId = UUID.randomUUID();
    String objectKey = "productos/" + productoId + "/principal-abc.webp";
    almacenDeImagenes.conObjeto(objectKey, 1000);

    assertThrows(
        ProductoNoEncontradoPorIdException.class,
        () ->
            confirmarImagenPrincipal.ejecutar(
                new ConfirmarImagenPrincipalComando(
                    productoId,
                    List.of(new VarianteSubida(100, objectKey)),
                    null,
                    100,
                    HASH,
                    "a",
                    "b")));
  }

  @Test
  void objectKeyDeOtroProductoLanzaIllegalArgument() {
    Producto producto = productoDePrueba();
    repositorioProductos.conProductos(producto);
    String objectKeyDeOtroProducto = "productos/" + UUID.randomUUID() + "/principal-abc.webp";
    almacenDeImagenes.conObjeto(objectKeyDeOtroProducto, 1000);

    assertThrows(
        IllegalArgumentException.class,
        () ->
            confirmarImagenPrincipal.ejecutar(
                new ConfirmarImagenPrincipalComando(
                    producto.id(),
                    List.of(new VarianteSubida(100, objectKeyDeOtroProducto)),
                    null,
                    100,
                    HASH,
                    "a",
                    "b")));
  }

  /**
   * La prueba que faltaba: la de "otro producto" ya estaba, pero la key peligrosa es la del
   * <b>mismo</b> producto y otro tipo. Con el prefijo corto pasaba las tres guardas —empieza por el
   * id, el objeto existe, tiene URL pública— y entonces la limpieza borraba los objetos {@code
   * principal-} de verdad, que no están en la lista de claves confirmadas. Pérdida irreversible, y
   * la principal apuntando a un objeto de galería que su propio borrado se lleva después.
   */
  @Test
  void unaKeyDeGaleriaDelMismoProductoNoSePuedeConfirmarComoPrincipal() {
    Producto producto = productoDePrueba();
    repositorioProductos.conProductos(producto);
    String keyDeGaleria = "productos/" + producto.id() + "/galeria-abc.webp";
    almacenDeImagenes.conObjeto(keyDeGaleria, 1000);

    assertThrows(
        IllegalArgumentException.class,
        () ->
            confirmarImagenPrincipal.ejecutar(
                new ConfirmarImagenPrincipalComando(
                    producto.id(),
                    List.of(new VarianteSubida(100, keyDeGaleria)),
                    null,
                    100,
                    HASH,
                    "a",
                    "b")));
  }

  /** Lo mismo con un fotograma del set de rotación, que se borra por prefijo entero. */
  @Test
  void unaKeyDeRotacionDelMismoProductoNoSePuedeConfirmarComoPrincipal() {
    Producto producto = productoDePrueba();
    repositorioProductos.conProductos(producto);
    String keyDeRotacion =
        "productos/" + producto.id() + "/rotacion/" + UUID.randomUUID() + "/0.webp";
    almacenDeImagenes.conObjeto(keyDeRotacion, 1000);

    assertThrows(
        IllegalArgumentException.class,
        () ->
            confirmarImagenPrincipal.ejecutar(
                new ConfirmarImagenPrincipalComando(
                    producto.id(),
                    List.of(new VarianteSubida(100, keyDeRotacion)),
                    null,
                    100,
                    HASH,
                    "a",
                    "b")));
  }

  /**
   * Y la vista previa entra por la misma puerta: es una clave más de la lista, así que colarla por
   * ahí tendría el mismo efecto que colarla como variante.
   */
  @Test
  void unaVistaPreviaQueNoEsDeLaPrincipalTampocoSeAcepta() {
    Producto producto = productoDePrueba();
    repositorioProductos.conProductos(producto);
    String keyBuena = "productos/" + producto.id() + "/principal-abc.webp";
    String vistaPreviaDeGaleria = "productos/" + producto.id() + "/galeria-xyz.jpg";
    almacenDeImagenes.conObjeto(keyBuena, 1000);
    almacenDeImagenes.conObjeto(vistaPreviaDeGaleria, 500);

    assertThrows(
        IllegalArgumentException.class,
        () ->
            confirmarImagenPrincipal.ejecutar(
                new ConfirmarImagenPrincipalComando(
                    producto.id(),
                    List.of(new VarianteSubida(100, keyBuena)),
                    vistaPreviaDeGaleria,
                    100,
                    HASH,
                    "a",
                    "b")));
  }

  @Test
  void objetoInexistenteEnElAlmacenLanzaObjetoDeImagenNoEncontrado() {
    Producto producto = productoDePrueba();
    repositorioProductos.conProductos(producto);
    String objectKey = "productos/" + producto.id() + "/principal-nunca-subido.webp";

    assertThrows(
        ObjetoDeImagenNoEncontradoException.class,
        () ->
            confirmarImagenPrincipal.ejecutar(
                new ConfirmarImagenPrincipalComando(
                    producto.id(),
                    List.of(new VarianteSubida(100, objectKey)),
                    null,
                    100,
                    HASH,
                    "a",
                    "b")));
  }

  @Test
  void borraLaImagenAnteriorDelBucket() {
    Producto producto = productoDePrueba();
    repositorioProductos.conProductos(producto);
    String anterior = "productos/" + producto.id() + "/principal-vieja.webp";
    String nueva = "productos/" + producto.id() + "/principal-nueva.webp";
    almacenDeImagenes.conObjeto(anterior, 30_000);
    almacenDeImagenes.conObjeto(nueva, 45_000);

    var confirmacion =
        confirmarImagenPrincipal.ejecutar(
            new ConfirmarImagenPrincipalComando(
                producto.id(),
                List.of(new VarianteSubida(1000, nueva)),
                null,
                800,
                HASH,
                "alt es",
                "alt en"));

    // La anterior se va —si no, cada reemplazo deja pagando un objeto que ya nadie sirve— y la
    // recién subida se queda, aunque compartan prefijo.
    assertEquals(1, confirmacion.objetosAnterioresBorrados());
    assertFalse(confirmacion.limpiezaFallida());
    assertFalse(almacenDeImagenes.existe(anterior));
    assertTrue(almacenDeImagenes.existe(nueva));
  }

  @Test
  void noTocaLosObjetosDeOtroProducto() {
    Producto producto = productoDePrueba();
    repositorioProductos.conProductos(producto);
    String nueva = "productos/" + producto.id() + "/principal-nueva.webp";
    String ajena = "productos/" + UUID.randomUUID() + "/principal-vieja.webp";
    almacenDeImagenes.conObjeto(nueva, 45_000);
    almacenDeImagenes.conObjeto(ajena, 30_000);

    var confirmacion =
        confirmarImagenPrincipal.ejecutar(
            new ConfirmarImagenPrincipalComando(
                producto.id(),
                List.of(new VarianteSubida(1000, nueva)),
                null,
                800,
                HASH,
                "alt es",
                "alt en"));

    assertEquals(0, confirmacion.objetosAnterioresBorrados());
    assertTrue(almacenDeImagenes.existe(ajena));
  }

  @Test
  void noTocaElSetDeRotacionDelMismoProducto() {
    Producto producto = productoDePrueba();
    repositorioProductos.conProductos(producto);
    String nueva = "productos/" + producto.id() + "/principal-nueva.webp";
    String fotograma = "productos/" + producto.id() + "/rotacion/set-1/0.webp";
    almacenDeImagenes.conObjeto(nueva, 45_000);
    almacenDeImagenes.conObjeto(fotograma, 40_000);

    confirmarImagenPrincipal.ejecutar(
        new ConfirmarImagenPrincipalComando(
            producto.id(),
            List.of(new VarianteSubida(1000, nueva)),
            null,
            800,
            HASH,
            "alt es",
            "alt en"));

    // El prefijo llega hasta "principal-" a propósito: con solo el id del producto, reemplazar la
    // imagen principal se llevaría por delante los fotogramas del visor 360.
    assertTrue(almacenDeImagenes.existe(fotograma));
  }

  @Test
  void siLaLimpiezaFallaLaImagenIgualQuedaGuardada() {
    Producto producto = productoDePrueba();
    repositorioProductos.conProductos(producto);
    String nueva = "productos/" + producto.id() + "/principal-nueva.webp";
    almacenDeImagenes.conObjeto(nueva, 45_000);
    almacenDeImagenes.fallarAlEliminar = true;

    var confirmacion =
        confirmarImagenPrincipal.ejecutar(
            new ConfirmarImagenPrincipalComando(
                producto.id(),
                List.of(new VarianteSubida(1000, nueva)),
                null,
                800,
                HASH,
                "alt es",
                "alt en"));

    // Para cuando se limpia, la imagen ya está guardada: reportar un fallo seria mentir sobre una
    // operacion que funciono. Lo que queda es basura en el bucket, y se dice.
    assertTrue(confirmacion.limpiezaFallida());
    assertEquals(confirmacion.imagen(), repositorioProductos.ultimaImagenPrincipal);
  }

  @Test
  void confirmaTresVariantesYLaImagenTomaLaMayorComoBase() {
    Producto producto = productoDePrueba();
    repositorioProductos.conProductos(producto);
    String base = "productos/" + producto.id() + "/principal-";
    almacenDeImagenes.conObjeto(base + "a.avif", 12_000);
    almacenDeImagenes.conObjeto(base + "b.avif", 30_000);
    almacenDeImagenes.conObjeto(base + "c.avif", 58_000);

    var imagen =
        confirmarImagenPrincipal
            .ejecutar(
                new ConfirmarImagenPrincipalComando(
                    producto.id(),
                    List.of(
                        new VarianteSubida(800, base + "b.avif"),
                        new VarianteSubida(1200, base + "c.avif"),
                        new VarianteSubida(480, base + "a.avif")),
                    null,
                    900,
                    HASH,
                    "alt es",
                    "alt en"))
            .imagen();

    assertEquals(List.of(480, 800, 1200), imagen.variantes().stream().map(v -> v.ancho()).toList());
    assertEquals(1200, imagen.ancho());
    assertEquals(58_000, imagen.bytes());
    assertTrue(imagen.url().endsWith(base + "c.avif"));
  }

  /**
   * La limpieza borra el prefijo {@code principal-} entero. Si conservara solo una de las claves
   * recién confirmadas, las otras dos se borrarían a sí mismas justo después de guardarse, y el
   * navegador pediría un objeto que ya no está porque nosotros se lo ofrecimos en el `srcset`.
   */
  @Test
  void laLimpiezaConservaTodasLasVariantesRecienSubidas() {
    Producto producto = productoDePrueba();
    repositorioProductos.conProductos(producto);
    String base = "productos/" + producto.id() + "/principal-";
    almacenDeImagenes.conObjeto(base + "vieja.avif", 90_000);
    almacenDeImagenes.conObjeto(base + "a.avif", 12_000);
    almacenDeImagenes.conObjeto(base + "b.avif", 58_000);
    almacenDeImagenes.conObjeto(base + "previa.jpg", 70_000);

    var confirmacion =
        confirmarImagenPrincipal.ejecutar(
            new ConfirmarImagenPrincipalComando(
                producto.id(),
                List.of(
                    new VarianteSubida(480, base + "a.avif"),
                    new VarianteSubida(1200, base + "b.avif")),
                base + "previa.jpg",
                900,
                HASH,
                "alt es",
                "alt en"));

    assertEquals(1, confirmacion.objetosAnterioresBorrados());
    assertFalse(almacenDeImagenes.existe(base + "vieja.avif"));
    assertTrue(almacenDeImagenes.existe(base + "a.avif"));
    assertTrue(almacenDeImagenes.existe(base + "b.avif"));
    assertTrue(almacenDeImagenes.existe(base + "previa.jpg"));
  }

  @Test
  void unaVarianteQueNuncaLlegoAlBucketTumbaLaConfirmacion() {
    Producto producto = productoDePrueba();
    repositorioProductos.conProductos(producto);
    String base = "productos/" + producto.id() + "/principal-";
    almacenDeImagenes.conObjeto(base + "a.avif", 12_000);

    assertThrows(
        ObjetoDeImagenNoEncontradoException.class,
        () ->
            confirmarImagenPrincipal.ejecutar(
                new ConfirmarImagenPrincipalComando(
                    producto.id(),
                    List.of(
                        new VarianteSubida(480, base + "a.avif"),
                        new VarianteSubida(1200, base + "nunca-subida.avif")),
                    null,
                    900,
                    HASH,
                    "alt es",
                    "alt en")));
  }

  @Test
  void laVistaPreviaSeGuardaYSeExigeQueExista() {
    Producto producto = productoDePrueba();
    repositorioProductos.conProductos(producto);
    String base = "productos/" + producto.id() + "/principal-";
    almacenDeImagenes.conObjeto(base + "a.avif", 12_000);

    assertThrows(
        ObjetoDeImagenNoEncontradoException.class,
        () ->
            confirmarImagenPrincipal.ejecutar(
                new ConfirmarImagenPrincipalComando(
                    producto.id(),
                    List.of(new VarianteSubida(480, base + "a.avif")),
                    base + "previa-que-no-subio.jpg",
                    900,
                    HASH,
                    "alt es",
                    "alt en")));

    almacenDeImagenes.conObjeto(base + "previa.jpg", 70_000);
    var imagen =
        confirmarImagenPrincipal
            .ejecutar(
                new ConfirmarImagenPrincipalComando(
                    producto.id(),
                    List.of(new VarianteSubida(480, base + "a.avif")),
                    base + "previa.jpg",
                    900,
                    HASH,
                    "alt es",
                    "alt en"))
            .imagen();

    assertTrue(imagen.urlVistaPrevia().orElseThrow().endsWith(base + "previa.jpg"));
  }

  @Test
  void unaVarianteDeOtroProductoSeRechaza() {
    Producto producto = productoDePrueba();
    repositorioProductos.conProductos(producto);
    String base = "productos/" + producto.id() + "/principal-";
    String ajena = "productos/" + UUID.randomUUID() + "/principal-ajena.avif";
    almacenDeImagenes.conObjeto(base + "a.avif", 12_000);
    almacenDeImagenes.conObjeto(ajena, 12_000);

    assertThrows(
        IllegalArgumentException.class,
        () ->
            confirmarImagenPrincipal.ejecutar(
                new ConfirmarImagenPrincipalComando(
                    producto.id(),
                    List.of(
                        new VarianteSubida(480, base + "a.avif"), new VarianteSubida(1200, ajena)),
                    null,
                    900,
                    HASH,
                    "alt es",
                    "alt en")));
  }

  /**
   * Tras usar una foto de la galería como principal, la principal anterior vive en la galería con
   * sus objetos {@code principal-}, y la vigente es un objeto {@code galeria-}. Subir otra
   * principal barría el prefijo entero y se llevaba la foto viva de la galería.
   */
  @Test
  void reemplazarLaPrincipalNoSeLlevaLaFotoDeLaGaleriaNiDejaLaAnteriorEnElBucket() {
    Producto producto = productoDePrueba();
    String keyPrincipalVieja = "productos/" + producto.id() + "/principal-vieja.webp";
    String keyGaleria = "productos/" + producto.id() + "/galeria-lado.webp";
    almacenDeImagenes.conObjeto(keyPrincipalVieja, 1000);
    almacenDeImagenes.conObjeto(keyGaleria, 1000);
    producto.asignarImagenPrincipal(
        imagenEn(TipoImagen.PRINCIPAL, keyPrincipalVieja, "%064x".formatted(1)));
    ImagenProducto lado = imagenEn(TipoImagen.GALERIA, keyGaleria, "%064x".formatted(2));
    producto.agregarImagenGaleria(lado);
    producto.usarImagenDeGaleriaComoPrincipal(lado.id());
    repositorioProductos.conProductos(producto);

    String keyNueva = "productos/" + producto.id() + "/principal-nueva.webp";
    almacenDeImagenes.conObjeto(keyNueva, 2000);
    var confirmacion =
        confirmarImagenPrincipal.ejecutar(
            new ConfirmarImagenPrincipalComando(
                producto.id(),
                List.of(new VarianteSubida(1000, keyNueva)),
                null,
                800,
                HASH,
                "alt es",
                "alt en"));

    assertTrue(almacenDeImagenes.existe(keyPrincipalVieja), "se borró una foto viva de la galería");
    assertFalse(
        almacenDeImagenes.existe(keyGaleria), "la principal reemplazada quedó en el bucket");
    assertTrue(almacenDeImagenes.existe(keyNueva));
    assertEquals(1, confirmacion.objetosAnterioresBorrados());
  }

  /**
   * Tras un intercambio, la galería tiene objetos `principal-`: no se pueden volver a confirmar.
   */
  @Test
  void unaKeyQueYaEsDeUnaFotoDeLaGaleriaNoSePuedeConfirmarComoPrincipal() {
    Producto producto = productoDePrueba();
    String keyVieja = "productos/" + producto.id() + "/principal-vieja.webp";
    String keyLado = "productos/" + producto.id() + "/galeria-lado.webp";
    almacenDeImagenes.conObjeto(keyVieja, 1000);
    almacenDeImagenes.conObjeto(keyLado, 1000);
    producto.asignarImagenPrincipal(imagenEn(TipoImagen.PRINCIPAL, keyVieja, "%064x".formatted(1)));
    ImagenProducto lado = imagenEn(TipoImagen.GALERIA, keyLado, "%064x".formatted(2));
    producto.agregarImagenGaleria(lado);
    producto.usarImagenDeGaleriaComoPrincipal(lado.id());
    repositorioProductos.conProductos(producto);

    assertThrows(
        IllegalArgumentException.class,
        () ->
            confirmarImagenPrincipal.ejecutar(
                new ConfirmarImagenPrincipalComando(
                    producto.id(),
                    List.of(new VarianteSubida(1000, keyVieja)),
                    null,
                    800,
                    HASH,
                    "alt es",
                    "alt en")));
    assertTrue(almacenDeImagenes.existe(keyVieja));
  }

  private ImagenProducto imagenEn(TipoImagen tipo, String objectKey, String hash) {
    return ImagenProducto.crear(
        tipo,
        0,
        List.of(new VarianteDeImagen(1000, almacenDeImagenes.urlPublica(objectKey), 1000)),
        null,
        800,
        new HashContenido(hash),
        "alt es",
        "alt en");
  }
}
