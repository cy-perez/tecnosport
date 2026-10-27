package co.tecnosport.api.domain.catalogo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.domain.compartido.HashContenido;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ImagenProductoTest {

  @Test
  void exigeAltEsYAltEnEnImagenPrincipal() {
    assertThrows(
        ImagenProductoInvalidaException.class, () -> imagen(TipoImagen.PRINCIPAL, "alt es", null));
  }

  @Test
  void aceptaImagenPrincipalConAltCompleto() {
    ImagenProducto imagen = imagen(TipoImagen.PRINCIPAL, "Camiseta azul", "Blue t-shirt");

    assertEquals("Camiseta azul", imagen.altEs());
    assertEquals("Blue t-shirt", imagen.altEn());
  }

  @Test
  void rechazaAltoNoPositivo() {
    assertThrows(
        ImagenProductoInvalidaException.class,
        () ->
            ImagenProducto.crear(
                TipoImagen.GALERIA,
                0,
                List.of(new VarianteDeImagen(800, "https://x/800.avif", 1000)),
                null,
                0,
                new HashContenido("%064x".formatted(1)),
                "Camiseta azul",
                "Blue t-shirt"));
  }

  @Test
  void rechazaUnaVarianteSinAncho() {
    assertThrows(
        ImagenProductoInvalidaException.class,
        () -> new VarianteDeImagen(0, "https://x/800.avif", 1000));
  }

  @Test
  void rechazaUnaImagenSinVariantes() {
    assertThrows(
        ImagenProductoInvalidaException.class,
        () ->
            ImagenProducto.crear(
                TipoImagen.PRINCIPAL,
                0,
                List.of(),
                null,
                600,
                new HashContenido("%064x".formatted(1)),
                "alt es",
                "alt en"));
  }

  @Test
  void rechazaDosVariantesConElMismoAncho() {
    assertThrows(
        ImagenProductoInvalidaException.class,
        () ->
            conVariantes(
                new VarianteDeImagen(800, "https://x/800.avif", 1000),
                new VarianteDeImagen(800, "https://x/otra-800.avif", 2000)));
  }

  @Test
  void ordenaLasVariantesDeMenorAMayor() {
    ImagenProducto imagen =
        conVariantes(
            new VarianteDeImagen(1200, "https://x/1200.avif", 3000),
            new VarianteDeImagen(480, "https://x/480.avif", 1000),
            new VarianteDeImagen(800, "https://x/800.avif", 2000));

    assertEquals(List.of(480, 800, 1200), imagen.variantes().stream().map(v -> v.ancho()).toList());
  }

  /**
   * La razón de ser del modelo: no hay una URL guardada aparte que pueda dejar de coincidir con las
   * variantes, como pasó con {@code urlWebp} desde ADR-0056.
   */
  @Test
  void laUrlElAnchoYLosBytesSalenDeLaVarianteMayor() {
    ImagenProducto imagen =
        conVariantes(
            new VarianteDeImagen(480, "https://x/480.avif", 1000),
            new VarianteDeImagen(1200, "https://x/1200.avif", 3000),
            new VarianteDeImagen(800, "https://x/800.avif", 2000));

    assertEquals("https://x/1200.avif", imagen.url());
    assertEquals(1200, imagen.ancho());
    assertEquals(3000, imagen.bytes());
  }

  @Test
  void laVistaPreviaEsOpcional() {
    assertTrue(imagen(TipoImagen.PRINCIPAL, "alt es", "alt en").urlVistaPrevia().isEmpty());
  }

  @Test
  void laVistaPreviaEnBlancoCuentaComoAusente() {
    ImagenProducto imagen =
        ImagenProducto.crear(
            TipoImagen.PRINCIPAL,
            0,
            List.of(new VarianteDeImagen(800, "https://x/800.avif", 1000)),
            "   ",
            600,
            new HashContenido("%064x".formatted(1)),
            "alt es",
            "alt en");

    assertTrue(imagen.urlVistaPrevia().isEmpty());
  }

  // --- la URL para quien no negocia formatos (Meta, los previsualizadores de enlaces) ---

  @Test
  void conVistaPreviaManalaVistaPrevia() {
    ImagenProducto imagen =
        ImagenProducto.crear(
            TipoImagen.PRINCIPAL,
            0,
            List.of(new VarianteDeImagen(800, "https://x/800.avif", 1000)),
            "https://x/previa.jpg",
            600,
            new HashContenido("%064x".formatted(1)),
            "alt es",
            "alt en");

    assertEquals(Optional.of("https://x/previa.jpg"), imagen.urlParaTercerosQueNoNegocianFormato());
  }

  /**
   * <b>El caso que faltaba y tenía la difusión en redes rota para medio catálogo.</b> Una foto
   * aprobada desde un borrador de proveedor se publica tal como llegó -- JPEG -- y nunca genera
   * vista previa, así que exigirla rechazaba, por no tener una conversión a JPEG, fotos que ya eran
   * JPEG.
   */
  @Test
  void sinVistaPreviaValeLaPropiaImagenCuandoYaEsJpeg() {
    assertEquals(
        Optional.of("https://x/800.jpg"),
        conVariantes(new VarianteDeImagen(800, "https://x/800.jpg", 1000))
            .urlParaTercerosQueNoNegocianFormato());
    assertEquals(
        Optional.of("https://x/800.png"),
        conVariantes(new VarianteDeImagen(800, "https://x/800.png", 1000))
            .urlParaTercerosQueNoNegocianFormato());
  }

  /** AVIF y WebP no: son justo los que el sitio sirve y los que estos terceros no muestran. */
  @Test
  void sinVistaPreviaUnAvifOUnWebpNoSirven() {
    assertTrue(
        conVariantes(new VarianteDeImagen(800, "https://x/800.avif", 1000))
            .urlParaTercerosQueNoNegocianFormato()
            .isEmpty());
    assertTrue(
        conVariantes(new VarianteDeImagen(800, "https://x/800.webp", 1000))
            .urlParaTercerosQueNoNegocianFormato()
            .isEmpty());
  }

  /**
   * Una URL sin extensión reconocible se trata como no publicable, que es el lado seguro: vale más
   * no publicar que publicar un post sin foto.
   */
  @Test
  void unaUrlSinExtensionConocidaNoSePublica() {
    assertTrue(
        conVariantes(new VarianteDeImagen(800, "https://x/800", 1000))
            .urlParaTercerosQueNoNegocianFormato()
            .isEmpty());
  }

  /** La cadena de consulta no cuenta: lo que decide el formato es la ruta. */
  @Test
  void laCadenaDeConsultaNoEscondeElFormato() {
    assertEquals(
        Optional.of("https://x/800.jpg?v=2"),
        conVariantes(new VarianteDeImagen(800, "https://x/800.jpg?v=2", 1000))
            .urlParaTercerosQueNoNegocianFormato());
  }

  /**
   * La principal que vale para todos los tonos no entra en la ficha si nadie lo pide (ADR-0069).
   */
  @Test
  void laPrincipalNoEntraEnLaFichaPorOmision() {
    ImagenProducto principal = imagen(TipoImagen.PRINCIPAL, "alt es", "alt en");

    assertFalse(principal.enLaFicha());
    assertTrue(principal.variantesQueMuestra().isEmpty());
  }

  @Test
  void soloLaPrincipalDecideSiEntraEnLaFicha() {
    ImagenProducto deGaleria = imagen(TipoImagen.GALERIA, "alt es", "alt en");

    assertThrows(
        ImagenProductoInvalidaException.class, () -> deGaleria.conLoQueMuestra(List.of(), true));
    assertTrue(
        imagen(TipoImagen.PRINCIPAL, "alt es", "alt en")
            .conLoQueMuestra(List.of(), true)
            .enLaFicha());
  }

  /** La foto de la camiseta blanca y negra: retrata las dos, y su propia variante no cuenta. */
  @Test
  void lasVariantesQueMuestraVanSinRepetirYSinLaPropia() {
    UUID propia = UUID.randomUUID();
    UUID blanca = UUID.randomUUID();
    UUID negra = UUID.randomUUID();
    ImagenProducto foto =
        ImagenProducto.crearDeVariante(
                TipoImagen.GALERIA,
                1,
                List.of(new VarianteDeImagen(800, "https://x/800.avif", 120_000)),
                null,
                600,
                new HashContenido("%064x".formatted(1)),
                "alt es",
                "alt en",
                propia)
            .conLoQueMuestra(List.of(blanca, propia, negra, blanca), false);

    assertEquals(List.of(blanca, negra), foto.variantesQueMuestra());
  }

  /**
   * Intercambiar la principal con una de la galería no pierde qué tonos retrata cada una. La
   * casilla de la ficha la suelta la que baja —la galería entra siempre— y la que sube la recibe de
   * quien llama, que la toma del puesto (ver {@code ProductoTest}).
   */
  @Test
  void elIntercambioConservaLoQueMuestraYSueltaLaCasilla() {
    UUID cocoa = UUID.randomUUID();
    ImagenProducto principal =
        imagen(TipoImagen.PRINCIPAL, "alt es", "alt en").conLoQueMuestra(List.of(cocoa), true);

    ImagenProducto enLaGaleria = principal.comoGaleria(3);
    ImagenProducto deVuelta = enLaGaleria.comoPrincipal();

    assertEquals(List.of(cocoa), enLaGaleria.variantesQueMuestra());
    assertFalse(enLaGaleria.enLaFicha());
    assertEquals(List.of(cocoa), deVuelta.variantesQueMuestra());
    assertFalse(deVuelta.enLaFicha());
    assertTrue(enLaGaleria.comoPrincipal(true).enLaFicha(), "la casilla la trae el puesto");
    assertTrue(principal.conOrden(0).enLaFicha());
  }

  /** Con color ya entra: la casilla no se guarda, para que no reviva si un día pierde el color. */
  @Test
  void unaPrincipalConColorNoGuardaLaCasilla() {
    ImagenProducto conColor =
        ImagenProducto.crearDeVariante(
            TipoImagen.PRINCIPAL,
            0,
            List.of(new VarianteDeImagen(800, "https://x/800.avif", 120_000)),
            null,
            600,
            new HashContenido("%064x".formatted(1)),
            "alt es",
            "alt en",
            UUID.randomUUID(),
            true,
            List.of());

    assertFalse(conColor.enLaFicha());
    assertFalse(conColor.conVariante(null).enLaFicha());
  }

  private static ImagenProducto conVariantes(VarianteDeImagen... variantes) {
    return ImagenProducto.crear(
        TipoImagen.PRINCIPAL,
        0,
        List.of(variantes),
        null,
        600,
        new HashContenido("%064x".formatted(1)),
        "alt es",
        "alt en");
  }

  private static ImagenProducto imagen(TipoImagen tipo, String altEs, String altEn) {
    return ImagenProducto.crear(
        tipo,
        0,
        List.of(new VarianteDeImagen(800, "https://x/800.avif", 120_000)),
        null,
        600,
        new HashContenido("%064x".formatted(1)),
        altEs,
        altEn);
  }
}
