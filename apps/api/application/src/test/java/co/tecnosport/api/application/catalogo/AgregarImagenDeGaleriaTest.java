package co.tecnosport.api.application.catalogo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.domain.catalogo.Categoria;
import co.tecnosport.api.domain.catalogo.GaleriaLlenaException;
import co.tecnosport.api.domain.catalogo.ImagenDeGaleriaDuplicadaException;
import co.tecnosport.api.domain.catalogo.ImagenProducto;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.catalogo.Marca;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.catalogo.TipoImagen;
import co.tecnosport.api.domain.compartido.Slug;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AgregarImagenDeGaleriaTest {

  private final RepositorioProductosFalso repositorioProductos = new RepositorioProductosFalso();
  private final AlmacenDeImagenesFalso almacenDeImagenes = new AlmacenDeImagenesFalso();
  private final AgregarImagenDeGaleria agregarImagenDeGaleria =
      new AgregarImagenDeGaleria(repositorioProductos, almacenDeImagenes);

  private static Producto productoDePrueba() {
    Marca marca = Marca.crear("JBL");
    Categoria categoria =
        Categoria.crear("Parlantes", new Slug("parlantes"), LineaCatalogo.TECNOLOGIA);
    return Producto.crear("JBL Charge 6", new Slug("jbl-charge-6"), "", marca, categoria);
  }

  private static String hash(int semilla) {
    return "%064x".formatted(semilla);
  }

  private ImagenProducto agregar(Producto producto, String sufijo, int semillaDelHash) {
    String objectKey = "productos/" + producto.id() + "/galeria-" + sufijo + ".jpg";
    almacenDeImagenes.conObjeto(objectKey, 120_000);
    return agregarImagenDeGaleria.ejecutar(
        new AgregarImagenDeGaleriaComando(
            producto.id(),
            List.of(new VarianteSubida(2000, objectKey)),
            null,
            2000,
            hash(semillaDelHash),
            "alt es",
            "alt en"));
  }

  @Test
  void agregaLaImagenAlProductoYLaGuarda() {
    Producto producto = productoDePrueba();
    repositorioProductos.conProductos(producto);

    ImagenProducto imagen = agregar(producto, "uno", 1);

    assertEquals(TipoImagen.GALERIA, imagen.tipo());
    assertEquals(2000, imagen.ancho());
    // Los bytes los pone el almacén, no el cliente: es el único dato de la imagen que el servidor
    // sí puede comprobar por su cuenta.
    assertEquals(120_000, imagen.bytes());
    assertEquals(producto.id(), repositorioProductos.ultimoProductoIdConImagenDeGaleria);
    assertEquals(java.util.List.of(imagen), repositorioProductos.imagenesDeGaleriaGuardadas);
    assertEquals(java.util.List.of(imagen), producto.galeria());
  }

  @Test
  void laSegundaImagenTomaElOrdenSiguiente() {
    Producto producto = productoDePrueba();
    repositorioProductos.conProductos(producto);

    ImagenProducto primera = agregar(producto, "uno", 1);
    ImagenProducto segunda = agregar(producto, "dos", 2);

    assertEquals(0, primera.orden());
    assertEquals(1, segunda.orden());
  }

  @Test
  void noBorraNadaDelBucket() {
    Producto producto = productoDePrueba();
    repositorioProductos.conProductos(producto);

    agregar(producto, "uno", 1);
    agregar(producto, "dos", 2);

    // Lo contrario de la imagen principal, que limpia su prefijo en cada confirmación. Si esto
    // limpiara, la segunda subida se llevaría la primera: comparten el prefijo 'galeria-'.
    assertTrue(almacenDeImagenes.prefijosEliminados.isEmpty());
    assertTrue(almacenDeImagenes.existe("productos/" + producto.id() + "/galeria-uno.jpg"));
  }

  @Test
  void laMismaFotoDosVecesNoEntraYNoSeGuarda() {
    Producto producto = productoDePrueba();
    repositorioProductos.conProductos(producto);
    agregar(producto, "uno", 7);

    assertThrows(ImagenDeGaleriaDuplicadaException.class, () -> agregar(producto, "otra-vez", 7));
    assertEquals(1, repositorioProductos.imagenesDeGaleriaGuardadas.size());
  }

  @Test
  void conLaGaleriaLlenaNoSeGuardaNada() {
    Producto producto = productoDePrueba();
    repositorioProductos.conProductos(producto);
    for (int i = 0; i < Producto.TOPE_DE_GALERIA; i++) {
      agregar(producto, "n" + i, i + 1);
    }

    assertThrows(GaleriaLlenaException.class, () -> agregar(producto, "una-mas", 99));
    assertEquals(Producto.TOPE_DE_GALERIA, repositorioProductos.imagenesDeGaleriaGuardadas.size());
  }

  @Test
  void objetoQueNuncaSeSubioLanzaObjetoDeImagenNoEncontrado() {
    Producto producto = productoDePrueba();
    repositorioProductos.conProductos(producto);
    String objectKey = "productos/" + producto.id() + "/galeria-nunca-subida.jpg";

    assertThrows(
        ObjetoDeImagenNoEncontradoException.class,
        () ->
            agregarImagenDeGaleria.ejecutar(
                new AgregarImagenDeGaleriaComando(
                    producto.id(),
                    List.of(new VarianteSubida(100, objectKey)),
                    null,
                    100,
                    hash(1),
                    "a",
                    "b")));
    assertTrue(repositorioProductos.imagenesDeGaleriaGuardadas.isEmpty());
  }

  /**
   * La guarda nació mirando solo {@code productos/{id}/}, copiada de la imagen principal. Con dos
   * prefijos por producto eso dejaba confirmar la key de la principal como imagen de galería, y el
   * siguiente reemplazo de la principal —que limpia su prefijo entero— borraba el objeto que la
   * galería estaba sirviendo.
   */
  @Test
  void unaKeyDeLaImagenPrincipalNoEntraComoImagenDeGaleria() {
    Producto producto = productoDePrueba();
    repositorioProductos.conProductos(producto);
    String deLaPrincipal = "productos/" + producto.id() + "/principal-abc.jpg";
    almacenDeImagenes.conObjeto(deLaPrincipal, 45_000);

    assertThrows(
        IllegalArgumentException.class,
        () ->
            agregarImagenDeGaleria.ejecutar(
                new AgregarImagenDeGaleriaComando(
                    producto.id(),
                    List.of(new VarianteSubida(100, deLaPrincipal)),
                    null,
                    100,
                    hash(1),
                    "a",
                    "b")));
    assertTrue(repositorioProductos.imagenesDeGaleriaGuardadas.isEmpty());
  }

  /**
   * El rechazo por hash no cubre este caso: el hash lo calcula el cliente. Dos filas apuntando al
   * mismo objeto romperían el borrado, que va por la key exacta y dejaría a la hermana rota.
   */
  @Test
  void elMismoObjetoDosVecesNoEntraAunqueElClienteCambieElHash() {
    Producto producto = productoDePrueba();
    repositorioProductos.conProductos(producto);
    String objectKey = "productos/" + producto.id() + "/galeria-uno.jpg";
    almacenDeImagenes.conObjeto(objectKey, 120_000);
    agregarImagenDeGaleria.ejecutar(
        new AgregarImagenDeGaleriaComando(
            producto.id(),
            List.of(new VarianteSubida(2000, objectKey)),
            null,
            2000,
            hash(1),
            "a",
            "b"));

    assertThrows(
        ImagenDeGaleriaDuplicadaException.class,
        () ->
            agregarImagenDeGaleria.ejecutar(
                new AgregarImagenDeGaleriaComando(
                    producto.id(),
                    List.of(new VarianteSubida(2000, objectKey)),
                    null,
                    2000,
                    hash(2),
                    "a",
                    "b")));
    assertEquals(1, repositorioProductos.imagenesDeGaleriaGuardadas.size());
  }

  @Test
  void objectKeyDeOtroProductoLanzaIllegalArgument() {
    Producto producto = productoDePrueba();
    repositorioProductos.conProductos(producto);
    String ajeno = "productos/" + UUID.randomUUID() + "/galeria-abc.jpg";
    almacenDeImagenes.conObjeto(ajeno, 1000);

    assertThrows(
        IllegalArgumentException.class,
        () ->
            agregarImagenDeGaleria.ejecutar(
                new AgregarImagenDeGaleriaComando(
                    producto.id(),
                    List.of(new VarianteSubida(100, ajeno)),
                    null,
                    100,
                    hash(1),
                    "a",
                    "b")));
  }

  @Test
  void productoInexistenteLanzaProductoNoEncontradoPorId() {
    UUID productoId = UUID.randomUUID();

    assertThrows(
        ProductoNoEncontradoPorIdException.class,
        () ->
            agregarImagenDeGaleria.ejecutar(
                new AgregarImagenDeGaleriaComando(
                    productoId,
                    List.of(
                        new VarianteSubida(100, "productos/" + productoId + "/galeria-abc.jpg")),
                    null,
                    100,
                    hash(1),
                    "a",
                    "b")));
  }

  @Test
  void agregaUnaImagenConVariasVariantesYLaMayorEsLaQueVaAGaleria() {
    Producto producto = productoDePrueba();
    repositorioProductos.conProductos(producto);
    String base = "productos/" + producto.id() + "/galeria-";
    almacenDeImagenes.conObjeto(base + "a.avif", 12_000);
    almacenDeImagenes.conObjeto(base + "b.avif", 58_000);
    almacenDeImagenes.conObjeto(base + "previa.jpg", 70_000);

    var imagen =
        agregarImagenDeGaleria.ejecutar(
            new AgregarImagenDeGaleriaComando(
                producto.id(),
                List.of(
                    new VarianteSubida(1200, base + "b.avif"),
                    new VarianteSubida(480, base + "a.avif")),
                base + "previa.jpg",
                900,
                hash(7),
                "alt es",
                "alt en"));

    assertEquals(List.of(480, 1200), imagen.variantes().stream().map(v -> v.ancho()).toList());
    assertTrue(imagen.url().endsWith(base + "b.avif"));
    assertTrue(imagen.urlVistaPrevia().orElseThrow().endsWith(base + "previa.jpg"));
  }

  @Test
  void unaVarianteDeGaleriaQueNuncaLlegoTumbaLaOperacion() {
    Producto producto = productoDePrueba();
    repositorioProductos.conProductos(producto);
    String base = "productos/" + producto.id() + "/galeria-";
    almacenDeImagenes.conObjeto(base + "a.avif", 12_000);

    assertThrows(
        ObjetoDeImagenNoEncontradoException.class,
        () ->
            agregarImagenDeGaleria.ejecutar(
                new AgregarImagenDeGaleriaComando(
                    producto.id(),
                    List.of(
                        new VarianteSubida(480, base + "a.avif"),
                        new VarianteSubida(1200, base + "nunca.avif")),
                    null,
                    900,
                    hash(8),
                    "alt es",
                    "alt en")));
  }
}
