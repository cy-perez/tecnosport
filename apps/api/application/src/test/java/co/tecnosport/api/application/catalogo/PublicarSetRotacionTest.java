package co.tecnosport.api.application.catalogo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import co.tecnosport.api.domain.catalogo.EstadoSetRotacion;
import co.tecnosport.api.domain.catalogo.ImagenProducto;
import co.tecnosport.api.domain.catalogo.SetRotacion;
import co.tecnosport.api.domain.catalogo.SetRotacionIncompletoException;
import co.tecnosport.api.domain.catalogo.TipoImagen;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PublicarSetRotacionTest {

  private static final UUID PRODUCTO = UUID.randomUUID();

  private final RepositorioSetsRotacionFalso repositorioSets = new RepositorioSetsRotacionFalso();
  private final PublicarSetRotacion publicarSetRotacion = new PublicarSetRotacion(repositorioSets);

  @Test
  void publicaUnSetCompleto() {
    SetRotacion set = completo();
    repositorioSets.con(set);

    SetRotacion publicado = publicarSetRotacion.ejecutar(set.id());

    assertEquals(EstadoSetRotacion.PUBLICADO, publicado.estado());
    assertEquals(publicado, repositorioSets.ultimoActualizado);
  }

  @Test
  void noPublicaUnSetQueSigueEnBorrador() {
    SetRotacion set = SetRotacion.abrir(PRODUCTO, 4, "admin:1", null, "iPhone 14", "v1");
    repositorioSets.con(set);

    assertThrows(
        SetRotacionIncompletoException.class, () -> publicarSetRotacion.ejecutar(set.id()));
  }

  @Test
  void unProductoNoTerminaConDosSetsPublicados() {
    SetRotacion anterior = completo();
    anterior.publicar();
    repositorioSets.con(anterior);
    SetRotacion nuevo = completo();
    repositorioSets.con(nuevo);

    assertThrows(
        SetRotacionPublicadoExistenteException.class,
        () -> publicarSetRotacion.ejecutar(nuevo.id()));
    assertEquals(EstadoSetRotacion.COMPLETO, nuevo.estado());
  }

  @Test
  void elSetPublicadoDeOtroProductoNoEstorba() {
    SetRotacion deOtroProducto =
        SetRotacion.abrir(UUID.randomUUID(), 4, "admin:1", null, "iPhone 14", "v1");
    for (int orden = 0; orden < 4; orden++) {
      deOtroProducto.agregarFotograma(fotograma(orden));
    }
    deOtroProducto.completar();
    deOtroProducto.publicar();
    repositorioSets.con(deOtroProducto);
    SetRotacion nuestro = completo();
    repositorioSets.con(nuestro);

    assertEquals(EstadoSetRotacion.PUBLICADO, publicarSetRotacion.ejecutar(nuestro.id()).estado());
  }

  @Test
  void setInexistenteLanzaSetNoEncontrado() {
    assertThrows(
        SetRotacionNoEncontradoException.class,
        () -> publicarSetRotacion.ejecutar(UUID.randomUUID()));
  }

  private static SetRotacion completo() {
    SetRotacion set = SetRotacion.abrir(PRODUCTO, 4, "admin:1", null, "iPhone 14", "v1");
    for (int orden = 0; orden < 4; orden++) {
      set.agregarFotograma(fotograma(orden));
    }
    set.completar();
    return set;
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
        "h" + orden,
        null,
        null);
  }
}
