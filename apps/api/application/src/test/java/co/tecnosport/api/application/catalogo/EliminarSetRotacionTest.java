package co.tecnosport.api.application.catalogo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.domain.catalogo.SetRotacion;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EliminarSetRotacionTest {

  private final RepositorioSetsRotacionFalso repositorioSets = new RepositorioSetsRotacionFalso();
  private final AlmacenDeImagenesFalso almacenDeImagenes = new AlmacenDeImagenesFalso();
  private final EliminarSetRotacion eliminarSetRotacion =
      new EliminarSetRotacion(repositorioSets, almacenDeImagenes);

  @Test
  void borraElSet() {
    SetRotacion set = SetRotacion.abrir(UUID.randomUUID(), 4, "admin:1", null, "iPhone 14", "v1");
    repositorioSets.con(set);

    eliminarSetRotacion.ejecutar(set.id());

    assertEquals(set.id(), repositorioSets.ultimoEliminado);
  }

  @Test
  void borraTambienLosObjetosDelBucket() {
    SetRotacion set = SetRotacion.abrir(UUID.randomUUID(), 4, "admin:1", null, "iPhone 14", "v1");
    repositorioSets.con(set);
    String prefijo = "productos/" + set.productoId() + "/rotacion/" + set.id() + "/";
    almacenDeImagenes.conObjeto(prefijo + "0.webp", 40_000);
    almacenDeImagenes.conObjeto(prefijo + "1.webp", 40_000);

    int borrados = eliminarSetRotacion.ejecutar(set.id());

    assertEquals(2, borrados);
    assertFalse(almacenDeImagenes.existe(prefijo + "0.webp"));
    assertFalse(almacenDeImagenes.existe(prefijo + "1.webp"));
  }

  @Test
  void borraLosObjetosDeUnSetQueMurioAMedioSubir() {
    // El set nunca llegó a COMPLETO, así que ninguno de estos objetos es una fila en la base de
    // datos. Recorrer los fotogramas conocidos los dejaría a todos en el bucket, para siempre.
    SetRotacion set = SetRotacion.abrir(UUID.randomUUID(), 4, "admin:1", null, "iPhone 14", "v1");
    repositorioSets.con(set);
    String prefijo = "productos/" + set.productoId() + "/rotacion/" + set.id() + "/";
    for (int orden = 0; orden < 4; orden++) {
      almacenDeImagenes.conObjeto(prefijo + orden + ".webp", 40_000);
    }
    assertEquals(0, set.fotogramas().size());

    assertEquals(4, eliminarSetRotacion.ejecutar(set.id()));

    for (int orden = 0; orden < 4; orden++) {
      assertFalse(almacenDeImagenes.existe(prefijo + orden + ".webp"));
    }
  }

  @Test
  void noTocaLosObjetosDeOtroSet() {
    SetRotacion set = SetRotacion.abrir(UUID.randomUUID(), 4, "admin:1", null, "iPhone 14", "v1");
    repositorioSets.con(set);
    String ajeno = "productos/" + set.productoId() + "/rotacion/" + UUID.randomUUID() + "/0.webp";
    almacenDeImagenes.conObjeto(ajeno, 40_000);

    assertEquals(0, eliminarSetRotacion.ejecutar(set.id()));
    assertTrue(almacenDeImagenes.existe(ajeno));
  }

  @Test
  void setInexistenteLanzaSetNoEncontrado() {
    assertThrows(
        SetRotacionNoEncontradoException.class,
        () -> eliminarSetRotacion.ejecutar(UUID.randomUUID()));
  }
}
