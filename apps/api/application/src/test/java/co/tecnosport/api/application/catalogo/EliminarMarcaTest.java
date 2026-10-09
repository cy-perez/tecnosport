package co.tecnosport.api.application.catalogo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.domain.catalogo.Marca;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EliminarMarcaTest {

  private final RepositorioMarcasFalso repositorio = new RepositorioMarcasFalso();
  private final EliminarMarca caso = new EliminarMarca(repositorio);

  @Test
  void borraLaQueNoTieneProductos() {
    Marca sobra = Marca.crear("Xaomi");
    Marca queda = Marca.crear("Xiaomi");
    repositorio.conMarcas(sobra, queda);

    caso.ejecutar(sobra.id());

    assertEquals(List.of(queda), repositorio.guardadas());
  }

  /** Con productos, en el estado que sea, no se borra y se dice cuántos. */
  @Test
  void conProductosSeRechazaDiciendoCuantos() {
    Marca xiaomi = Marca.crear("Xiaomi");
    repositorio.conMarcas(xiaomi);
    repositorio.conProductosEn(xiaomi, 7);

    MarcaConProductosException error =
        assertThrows(MarcaConProductosException.class, () -> caso.ejecutar(xiaomi.id()));

    assertTrue(error.getMessage().contains("7 productos"), error.getMessage());
    assertEquals(List.of(xiaomi), repositorio.guardadas());
  }

  @Test
  void unaMarcaQueNoExisteEsNoEncontrada() {
    assertThrows(MarcaNoEncontradaException.class, () -> caso.ejecutar(UUID.randomUUID()));
  }
}
