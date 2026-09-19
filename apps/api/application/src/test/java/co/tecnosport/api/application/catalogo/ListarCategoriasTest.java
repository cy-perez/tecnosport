package co.tecnosport.api.application.catalogo;

import static org.junit.jupiter.api.Assertions.assertEquals;

import co.tecnosport.api.domain.catalogo.Categoria;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.compartido.Slug;
import java.util.List;
import org.junit.jupiter.api.Test;

class ListarCategoriasTest {

  @Test
  void ofreceSoloLasCategoriasQueTienenAlgoPublicado() {
    RepositorioCategoriasFalso repositorio = new RepositorioCategoriasFalso();
    Categoria celulares =
        Categoria.crear("Celulares", new Slug("celulares"), LineaCatalogo.TECNOLOGIA);
    Categoria proyectores =
        Categoria.crear("Proyectores", new Slug("proyectores"), LineaCatalogo.TECNOLOGIA);
    repositorio.conCategorias(celulares, proyectores);
    repositorio.conCategoriasConProductosPublicados(celulares);

    List<Categoria> resultado = new ListarCategorias(repositorio).ejecutar();

    assertEquals(List.of(celulares), resultado);
  }

  /**
   * "Proyectores" es el caso real: {@code V38} la dio de alta el 14 de septiembre y nunca tuvo un
   * producto detrás. La vitrina la ofrecía igual.
   */
  @Test
  void noOfreceUnaCategoriaSinProductosAunqueExista() {
    RepositorioCategoriasFalso repositorio = new RepositorioCategoriasFalso();
    repositorio.conCategorias(
        Categoria.crear("Proyectores", new Slug("proyectores"), LineaCatalogo.TECNOLOGIA));
    repositorio.conCategoriasConProductosPublicados();

    assertEquals(List.of(), new ListarCategorias(repositorio).ejecutar());
  }
}
