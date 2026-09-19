package co.tecnosport.api.application.catalogo;

import static org.junit.jupiter.api.Assertions.assertEquals;

import co.tecnosport.api.domain.catalogo.Categoria;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.compartido.Slug;
import java.util.List;
import org.junit.jupiter.api.Test;

class ListarCategoriasAdminTest {

  @Test
  void ofreceTambienLasCategoriasSinProductos() {
    RepositorioCategoriasFalso repositorio = new RepositorioCategoriasFalso();
    Categoria celulares =
        Categoria.crear("Celulares", new Slug("celulares"), LineaCatalogo.TECNOLOGIA);
    Categoria proyectores =
        Categoria.crear("Proyectores", new Slug("proyectores"), LineaCatalogo.TECNOLOGIA);
    repositorio.conCategorias(celulares, proyectores);
    repositorio.conCategoriasConProductosPublicados(celulares);

    List<Categoria> resultado = new ListarCategoriasAdmin(repositorio).ejecutar();

    assertEquals(List.of(celulares, proyectores), resultado);
  }
}
