package co.tecnosport.api.application.catalogo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.domain.catalogo.ImagenProducto;
import co.tecnosport.api.domain.catalogo.SetRotacion;
import co.tecnosport.api.domain.catalogo.SetRotacionIncompletoException;
import co.tecnosport.api.domain.catalogo.TipoImagen;
import co.tecnosport.api.domain.compartido.HashContenido;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SolicitarSubidasDeRotacionTest {

  private static final UUID PRODUCTO = UUID.randomUUID();

  private final RepositorioSetsRotacionFalso repositorioSets = new RepositorioSetsRotacionFalso();
  private final AlmacenDeImagenesFalso almacenDeImagenes = new AlmacenDeImagenesFalso();
  private final SolicitarSubidasDeRotacion solicitarSubidas =
      new SolicitarSubidasDeRotacion(repositorioSets, almacenDeImagenes);

  @Test
  void emiteUnaUrlPorFotogramaPrometido() {
    SetRotacion set = abierto(8);
    repositorioSets.con(set);

    List<SubidaDeFotograma> subidas =
        solicitarSubidas.ejecutar(new SolicitarSubidasDeRotacionComando(set.id(), "image/webp"));

    assertEquals(8, subidas.size());
    assertEquals(
        List.of(0, 1, 2, 3, 4, 5, 6, 7), subidas.stream().map(SubidaDeFotograma::orden).toList());
    for (SubidaDeFotograma subida : subidas) {
      String esperada =
          "productos/" + PRODUCTO + "/rotacion/" + set.id() + "/" + subida.orden() + ".webp";
      assertEquals(esperada, subida.objectKey());
      assertTrue(subida.url().contains(subida.objectKey()));
    }
  }

  @Test
  void cuantasUrlSeEmitenLoDecideElSetYNoElCliente() {
    SetRotacion set = abierto(4);
    repositorioSets.con(set);

    List<SubidaDeFotograma> subidas =
        solicitarSubidas.ejecutar(new SolicitarSubidasDeRotacionComando(set.id(), "image/webp"));

    assertEquals(4, subidas.size());
  }

  @Test
  void setInexistenteLanzaSetNoEncontrado() {
    assertThrows(
        SetRotacionNoEncontradoException.class,
        () ->
            solicitarSubidas.ejecutar(
                new SolicitarSubidasDeRotacionComando(UUID.randomUUID(), "image/webp")));
  }

  @Test
  void unSetYaCompletoNoRecibeMasSubidas() {
    SetRotacion set = abierto(4);
    for (int orden = 0; orden < 4; orden++) {
      set.agregarFotograma(fotograma(orden));
    }
    set.completar();
    repositorioSets.con(set);

    assertThrows(
        SetRotacionIncompletoException.class,
        () ->
            solicitarSubidas.ejecutar(
                new SolicitarSubidasDeRotacionComando(set.id(), "image/webp")));
  }

  @Test
  void rechazaUnTipoDeContenidoQueNoEsImagenSoportada() {
    SetRotacion set = abierto(4);
    repositorioSets.con(set);

    assertThrows(
        IllegalArgumentException.class,
        () ->
            solicitarSubidas.ejecutar(
                new SolicitarSubidasDeRotacionComando(set.id(), "application/pdf")));
  }

  private static SetRotacion abierto(int fotogramas) {
    return SetRotacion.abrir(PRODUCTO, fotogramas, "admin:1", null, "iPhone 14", "v1");
  }

  private static ImagenProducto fotograma(int orden) {
    return ImagenProducto.crear(
        TipoImagen.ROTACION,
        orden,
        "https://x/" + orden,
        "https://x/" + orden,
        1000,
        1000,
        900,
        new HashContenido("%064x".formatted(orden)),
        null,
        null);
  }
}
