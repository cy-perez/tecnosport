package co.tecnosport.api.domain.catalogo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
  void rechazaDimensionesNoPositivas() {
    assertThrows(
        ImagenProductoInvalidaException.class,
        () ->
            ImagenProducto.crear(
                TipoImagen.ROTACION,
                0,
                "https://x/1.jpg",
                "https://x/1.webp",
                0,
                600,
                1000,
                "hash",
                "",
                ""));
  }

  private static ImagenProducto imagen(TipoImagen tipo, String altEs, String altEn) {
    return ImagenProducto.crear(
        tipo, 0, "https://x/1.jpg", "https://x/1.webp", 800, 600, 120_000, "hash-1", altEs, altEn);
  }
}
