package co.tecnosport.api.domain.catalogo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SetRotacionTest {

  private static final UUID PRODUCTO = UUID.randomUUID();

  @Test
  void seAbreVacioYEnBorrador() {
    SetRotacion set = abrir(8);

    assertEquals(EstadoSetRotacion.BORRADOR, set.estado());
    assertEquals(List.of(), set.fotogramas());
    assertEquals(8, set.fotogramasPrometidos());
    assertEquals(PRODUCTO, set.productoId());
  }

  @Test
  void noSeAbreConMenosDeCuatroFotogramasNiConMasDeDieciseis() {
    assertThrows(SetRotacionIncompletoException.class, () -> abrir(3));
    assertThrows(SetRotacionIncompletoException.class, () -> abrir(17));
  }

  @Test
  void unSetIncompletoNoSeCompletaNiSePublica() {
    SetRotacion set = abrir(8);
    agregar(set, 0, 1, 2, 3);

    assertThrows(SetRotacionIncompletoException.class, set::completar);
    assertThrows(SetRotacionIncompletoException.class, set::publicar);
    assertEquals(EstadoSetRotacion.BORRADOR, set.estado());
  }

  @Test
  void seCompletaYSePublicaCuandoLleganTodosLosPrometidos() {
    SetRotacion set = abrir(4);
    agregar(set, 0, 1, 2, 3);

    set.completar();
    assertEquals(EstadoSetRotacion.COMPLETO, set.estado());

    set.publicar();
    assertEquals(EstadoSetRotacion.PUBLICADO, set.estado());
  }

  @Test
  void rechazaUnFotogramaQueNoEsDeRotacion() {
    SetRotacion set = abrir(4);
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

    assertThrows(SetRotacionIncompletoException.class, () -> set.agregarFotograma(principal));
  }

  @Test
  void rechazaUnOrdenFueraDeLoPrometido() {
    SetRotacion set = abrir(4);

    assertThrows(SetRotacionIncompletoException.class, () -> set.agregarFotograma(fotograma(4)));
  }

  @Test
  void rechazaDosFotogramasEnLaMismaPosicion() {
    SetRotacion set = abrir(4);
    set.agregarFotograma(fotograma(2));

    assertThrows(SetRotacionIncompletoException.class, () -> set.agregarFotograma(fotograma(2)));
  }

  @Test
  void aUnSetYaCompletoNoSeLeAgreganFotogramas() {
    SetRotacion set = abrir(4);
    agregar(set, 0, 1, 2, 3);
    set.completar();

    assertThrows(SetRotacionIncompletoException.class, () -> set.agregarFotograma(fotograma(0)));
  }

  @Test
  void noSeCompletaDosVeces() {
    SetRotacion set = abrir(4);
    agregar(set, 0, 1, 2, 3);
    set.completar();

    assertThrows(SetRotacionIncompletoException.class, set::completar);
    assertEquals(EstadoSetRotacion.COMPLETO, set.estado());
  }

  @Test
  void losFotogramasSalenEnOrdenAunqueLleguenDesordenados() {
    SetRotacion set = abrir(4);
    agregar(set, 3, 0, 2, 1);

    assertEquals(
        List.of(0, 1, 2, 3), set.fotogramas().stream().map(ImagenProducto::orden).toList());
  }

  @Test
  void unSetReconstruidoConservaLoPrometidoYSuEstado() {
    SetRotacion set =
        new SetRotacion(
            UUID.randomUUID(),
            PRODUCTO,
            8,
            List.of(fotograma(0), fotograma(1), fotograma(2), fotograma(3)),
            EstadoSetRotacion.BORRADOR,
            "admin",
            null,
            "iPhone 14",
            "v1");

    assertEquals(8, set.fotogramasPrometidos());
    assertEquals(4, set.fotogramas().size());
    // Reconstruir no completa nada: le siguen faltando cuatro fotogramas.
    assertThrows(SetRotacionIncompletoException.class, set::completar);
  }

  private static SetRotacion abrir(int fotogramasPrometidos) {
    return SetRotacion.abrir(PRODUCTO, fotogramasPrometidos, "admin", null, "iPhone 14", "v1");
  }

  private static void agregar(SetRotacion set, int... ordenes) {
    for (int orden : ordenes) {
      set.agregarFotograma(fotograma(orden));
    }
  }

  private static ImagenProducto fotograma(int orden) {
    return ImagenProducto.crear(
        TipoImagen.ROTACION,
        orden,
        "https://x/" + orden + ".jpg",
        "https://x/" + orden + ".webp",
        1000,
        1000,
        1000,
        "hash-" + orden,
        null,
        null);
  }
}
