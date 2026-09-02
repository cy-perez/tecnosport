package co.tecnosport.api.domain.catalogo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SetRotacionTest {

  @Test
  void unSetIncompletoNoSePublica() {
    SetRotacion set = SetRotacion.iniciar(fotogramas(3), "admin", null, "iPhone 14", "v1");

    assertThrows(SetRotacionIncompletoException.class, set::completar);
    assertThrows(SetRotacionIncompletoException.class, set::publicar);
    assertEquals(EstadoSetRotacion.BORRADOR, set.estado());
  }

  @Test
  void rechazaMasDeDieciseisFotogramas() {
    SetRotacion set = SetRotacion.iniciar(fotogramas(17), "admin", null, "iPhone 14", "v1");

    assertThrows(SetRotacionIncompletoException.class, set::completar);
  }

  @Test
  void rechazaOrdenConHuecos() {
    List<ImagenProducto> fotogramas = fotogramas(4);
    List<ImagenProducto> conHueco = new ArrayList<>(fotogramas.subList(0, 3));
    conHueco.add(fotograma(10));
    SetRotacion set = SetRotacion.iniciar(conHueco, "admin", null, "iPhone 14", "v1");

    assertThrows(SetRotacionIncompletoException.class, set::completar);
  }

  @Test
  void seCompletaYSePublicaConCuatroFotogramasBienOrdenados() {
    SetRotacion set = SetRotacion.iniciar(fotogramas(4), "admin", null, "iPhone 14", "v1");

    set.completar();
    assertEquals(EstadoSetRotacion.COMPLETO, set.estado());

    set.publicar();
    assertEquals(EstadoSetRotacion.PUBLICADO, set.estado());
  }

  @Test
  void rechazaFotogramaQueNoEsDeTipoRotacion() {
    ImagenProducto principal =
        ImagenProducto.crear(
            TipoImagen.PRINCIPAL,
            0,
            "https://x/0.jpg",
            "https://x/0.webp",
            800,
            600,
            1000,
            "h0",
            "alt",
            "alt");

    assertThrows(
        SetRotacionIncompletoException.class,
        () -> SetRotacion.iniciar(List.of(principal), "admin", null, "iPhone 14", "v1"));
  }

  private static List<ImagenProducto> fotogramas(int cantidad) {
    List<ImagenProducto> fotogramas = new ArrayList<>();
    for (int i = 0; i < cantidad; i++) {
      fotogramas.add(fotograma(i));
    }
    return fotogramas;
  }

  private static ImagenProducto fotograma(int orden) {
    return ImagenProducto.crear(
        TipoImagen.ROTACION,
        orden,
        "https://x/" + orden + ".jpg",
        "https://x/" + orden + ".webp",
        800,
        600,
        1000,
        "hash-" + orden,
        null,
        null);
  }
}
