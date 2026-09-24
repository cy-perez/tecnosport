package co.tecnosport.api.application.catalogo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.domain.catalogo.Categoria;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.compartido.Slug;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EliminarCategoriaTest {

  private final RepositorioCategoriasFalso repositorio = new RepositorioCategoriasFalso();
  private final EliminarCategoria eliminar = new EliminarCategoria(repositorio);

  @Test
  void borraUnaCategoriaVacia() {
    Categoria proyectores =
        Categoria.crear("Proyectores", new Slug("proyectores"), LineaCatalogo.TECNOLOGIA);
    repositorio.conCategorias(proyectores);

    eliminar.ejecutar(proyectores.id());

    assertEquals(List.of(), repositorio.listarTodas());
  }

  /**
   * El caso que justifica que no haya cascada: desde el panel, "Dama" y "Faldas" se ven igual de
   * borrables, y "Dama" arrastra nueve.
   */
  @Test
  void noBorraUnaRamaConHojas() {
    Categoria dama = Categoria.crear("Dama", new Slug("ropa-dama"), LineaCatalogo.ROPA);
    Categoria faldas = Categoria.crearBajo(dama, "Faldas", new Slug("ropa-dama-faldas"));
    repositorio.conCategorias(dama, faldas);

    CategoriaConHijasException error =
        assertThrows(CategoriaConHijasException.class, () -> eliminar.ejecutar(dama.id()));

    assertTrue(error.getMessage().contains("1 subcategoría"));
    assertEquals(2, repositorio.listarTodas().size());
  }

  @Test
  void noBorraUnaCategoriaConProductos() {
    Categoria celulares =
        Categoria.crear("Celulares", new Slug("celulares"), LineaCatalogo.TECNOLOGIA);
    repositorio.conCategorias(celulares);
    repositorio.conProductosEn(celulares);

    assertThrows(CategoriaConProductosException.class, () -> eliminar.ejecutar(celulares.id()));
    assertEquals(1, repositorio.listarTodas().size());
  }

  /**
   * Un borrador también cuenta, y eso es una decisión: es trabajo de alguien, y borrarle la
   * categoría por debajo lo rompe igual que si estuviera publicado. Lo defiende el doble, que no
   * distingue estado, y la consulta JPA, que tampoco.
   */
  @Test
  void noBorraUnaCategoriaQueSoloTieneBorradores() {
    Categoria tablets = Categoria.crear("Tablets", new Slug("tablets"), LineaCatalogo.TECNOLOGIA);
    repositorio.conCategorias(tablets);
    repositorio.conProductosEn(tablets);

    assertThrows(CategoriaConProductosException.class, () -> eliminar.ejecutar(tablets.id()));
  }

  @Test
  void rechazaUnaCategoriaQueNoExiste() {
    assertThrows(CategoriaNoEncontradaException.class, () -> eliminar.ejecutar(UUID.randomUUID()));
  }
}
