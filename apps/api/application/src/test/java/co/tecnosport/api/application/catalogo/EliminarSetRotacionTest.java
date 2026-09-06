package co.tecnosport.api.application.catalogo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import co.tecnosport.api.domain.catalogo.SetRotacion;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EliminarSetRotacionTest {

  private final RepositorioSetsRotacionFalso repositorioSets = new RepositorioSetsRotacionFalso();
  private final EliminarSetRotacion eliminarSetRotacion = new EliminarSetRotacion(repositorioSets);

  @Test
  void borraElSet() {
    SetRotacion set = SetRotacion.abrir(UUID.randomUUID(), 4, "admin:1", null, "iPhone 14", "v1");
    repositorioSets.con(set);

    eliminarSetRotacion.ejecutar(set.id());

    assertEquals(set.id(), repositorioSets.ultimoEliminado);
  }

  @Test
  void setInexistenteLanzaSetNoEncontrado() {
    assertThrows(
        SetRotacionNoEncontradoException.class,
        () -> eliminarSetRotacion.ejecutar(UUID.randomUUID()));
  }
}
