package co.tecnosport.api.domain.catalogo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.domain.compartido.HashContenido;
import java.util.List;
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
  void permiteAltVacioEnFotogramaDeRotacion() {
    ImagenProducto imagen = imagen(TipoImagen.ROTACION, null, null);

    assertEquals("", imagen.altEs());
    assertEquals("", imagen.altEn());
  }

  @Test
  void rechazaAltoNoPositivo() {
    assertThrows(
        ImagenProductoInvalidaException.class,
        () ->
            ImagenProducto.crear(
                TipoImagen.ROTACION,
                0,
                List.of(new VarianteDeImagen(800, "https://x/800.avif", 1000)),
                null,
                0,
                new HashContenido("%064x".formatted(1)),
                "",
                ""));
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
