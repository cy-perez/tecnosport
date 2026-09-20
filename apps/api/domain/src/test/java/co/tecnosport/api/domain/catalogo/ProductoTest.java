package co.tecnosport.api.domain.catalogo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import co.tecnosport.api.domain.compartido.HashContenido;
import co.tecnosport.api.domain.compartido.Sku;
import co.tecnosport.api.domain.compartido.Slug;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProductoTest {

  @Test
  void noSePublicaSinImagenPrincipal() {
    Producto producto = productoDePrueba();

    assertThrows(ProductoSinImagenPrincipalException.class, producto::publicar);
    assertEquals(EstadoProducto.BORRADOR, producto.estado());
  }

  @Test
  void sePublicaConImagenPrincipal() {
    Producto producto = productoDePrueba();
    producto.asignarImagenPrincipal(imagenPrincipal());

    producto.publicar();

    assertEquals(EstadoProducto.PUBLICADO, producto.estado());
  }

  @Test
  void rechazaSkuDuplicadoEnElMismoProducto() {
    Producto producto = productoDePrueba();
    producto.agregarVariante(variante("TS-CAM-AZ-M"));

    assertThrows(
        SkuDuplicadoException.class, () -> producto.agregarVariante(variante("TS-CAM-AZ-M")));
  }

  @Test
  void actualizarDatosBasicosCambiaNombreDescripcionMarcaYCategoriaSinTocarElSlug() {
    Producto producto = productoDePrueba();
    Slug slugOriginal = producto.slug();
    Marca nuevaMarca = Marca.crear("Under Trail");
    Categoria nuevaCategoria = Categoria.crear("Bolsos", new Slug("bolsos"), LineaCatalogo.BOLSOS);

    producto.actualizarDatosBasicos(
        "Camiseta renombrada", "Nueva descripción", nuevaMarca, nuevaCategoria);

    assertEquals("Camiseta renombrada", producto.nombre());
    assertEquals("Nueva descripción", producto.descripcion());
    assertEquals(nuevaMarca, producto.marca());
    assertEquals(nuevaCategoria, producto.categoria());
    assertEquals(slugOriginal, producto.slug());
  }

  @Test
  void actualizarDatosBasicosRechazaNombreVacio() {
    Producto producto = productoDePrueba();

    assertThrows(
        co.tecnosport.api.domain.compartido.ExcepcionDeDominio.class,
        () ->
            producto.actualizarDatosBasicos(
                "", "descripción", producto.marca(), producto.categoria()));
  }

  @Test
  void rechazaImagenPrincipalDeOtroTipo() {
    Producto producto = productoDePrueba();
    ImagenProducto galeria =
        ImagenProducto.crear(
            TipoImagen.GALERIA,
            0,
            "https://x/1.jpg",
            "https://x/1.webp",
            800,
            600,
            1000,
            new HashContenido("%064x".formatted(1)),
            "alt",
            "alt");

    assertThrows(
        ImagenProductoInvalidaException.class, () -> producto.asignarImagenPrincipal(galeria));
  }

  private static Producto productoDePrueba() {
    Marca marca = Marca.crear("TecnoSport");
    Categoria categoria =
        Categoria.crear("Ropa deportiva", new Slug("ropa-deportiva"), LineaCatalogo.ROPA_Y_CALZADO);
    return Producto.crear(
        "Camiseta running Dry-Fit",
        new Slug("camiseta-running-dry-fit"),
        "Descripción",
        marca,
        categoria);
  }

  @Test
  void medirVarianteGrabaElPaqueteYDevuelveLaVarianteYaMedida() {
    Producto producto = productoDePrueba();
    Variante sinMedir =
        Variante.crear(
            new Sku("TS-SIN-MEDIR"),
            Dinero.deCop(89_900),
            new BigDecimal("0.19"),
            5,
            null,
            null,
            List.of());
    producto.agregarVariante(sinMedir);

    Variante medida = producto.medirVariante(sinMedir.id(), new Paquete(430, 17, 9, 5));

    assertEquals(Optional.of(new Paquete(430, 17, 9, 5)), medida.paquete());
    assertEquals(1, producto.variantes().size());
    assertEquals(Optional.of(new Paquete(430, 17, 9, 5)), producto.variantes().get(0).paquete());
    assertEquals(new Sku("TS-SIN-MEDIR"), producto.variantes().get(0).sku());
  }

  /**
   * La única regla que este método existe para proteger. Sin ella, un caso de uso que cargue un
   * producto por un lado y aplique la medida por otro escribiría el peso de un parlante en un
   * celular sin que nada se queje: las dos cosas son cuatro enteros positivos.
   */
  @Test
  void noSePuedeMedirUnaVarianteDeOtroProducto() {
    Producto producto = productoDePrueba();
    producto.agregarVariante(variante("TS-PROPIA"));
    UUID ajena = UUID.randomUUID();

    ExcepcionDeDominio excepcion =
        assertThrows(
            ExcepcionDeDominio.class,
            () -> producto.medirVariante(ajena, new Paquete(430, 17, 9, 5)));
    assertTrue(excepcion.getMessage().contains(ajena.toString()));
  }

  private static ImagenProducto imagenPrincipal() {
    return ImagenProducto.crear(
        TipoImagen.PRINCIPAL,
        0,
        "https://x/0.jpg",
        "https://x/0.webp",
        800,
        600,
        1000,
        new HashContenido("%064x".formatted(0)),
        "alt es",
        "alt en");
  }

  private static Variante variante(String sku) {
    return Variante.crear(
        new Sku(sku),
        Dinero.deCop(89_900),
        new BigDecimal("0.19"),
        5,
        null,
        new Paquete(180, 30, 25, 4),
        List.of());
  }
}
