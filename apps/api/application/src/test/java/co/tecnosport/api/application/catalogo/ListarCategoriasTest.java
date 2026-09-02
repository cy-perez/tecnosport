package co.tecnosport.api.application.catalogo;

import static org.junit.jupiter.api.Assertions.assertEquals;

import co.tecnosport.api.domain.catalogo.Categoria;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.compartido.Slug;
import java.util.List;
import org.junit.jupiter.api.Test;

class ListarCategoriasTest {

  @Test
  void devuelveTodasLasCategoriasDelRepositorio() {
    RepositorioCategoriasFalso repositorio = new RepositorioCategoriasFalso();
    Categoria bolsos = Categoria.crear("Bolsos", new Slug("bolsos"), LineaCatalogo.BOLSOS);
    Categoria celulares =
        Categoria.crear("Celulares", new Slug("celulares"), LineaCatalogo.CELULARES);
    repositorio.conCategorias(bolsos, celulares);

    List<Categoria> resultado = new ListarCategorias(repositorio).ejecutar();

    assertEquals(List.of(bolsos, celulares), resultado);
  }
}
