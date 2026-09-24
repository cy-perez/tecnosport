package co.tecnosport.api.application.catalogo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.domain.catalogo.Categoria;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.compartido.Slug;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Esta prueba afirmaba lo contrario hasta el 24 de septiembre de 2026: que la vitrina solo veía las
 * categorías con algo publicado detrás. El caso que la justificaba —"Proyectores", dada de alta por
 * {@code V38} y sin un solo producto— sigue siendo real, y aun así la decisión se invirtió al
 * llegar el árbol: el menú pinta la rama entera, y saltarse "Faldas" porque hoy no hay ninguna no
 * es esconder un filtro vacío, es decirle al comprador que no vendemos faldas.
 *
 * <p>Se deja escrito aquí y no solo en el javadoc del caso de uso porque una prueba que cambia de
 * bando sin explicar por qué es la que alguien "arregla" de vuelta seis meses después.
 */
class ListarCategoriasTest {

  @Test
  void ofreceTodasLasCategorias() {
    RepositorioCategoriasFalso repositorio = new RepositorioCategoriasFalso();
    Categoria celulares =
        Categoria.crear("Celulares", new Slug("celulares"), LineaCatalogo.TECNOLOGIA);
    Categoria proyectores =
        Categoria.crear("Proyectores", new Slug("proyectores"), LineaCatalogo.TECNOLOGIA);
    repositorio.conCategorias(celulares, proyectores);
    repositorio.conProductosEn(celulares);

    List<Categoria> resultado = new ListarCategorias(repositorio).ejecutar();

    assertEquals(List.of(celulares, proyectores), resultado);
  }

  @Test
  void ofreceUnaCategoriaVaciaIgual() {
    RepositorioCategoriasFalso repositorio = new RepositorioCategoriasFalso();
    Categoria proyectores =
        Categoria.crear("Proyectores", new Slug("proyectores"), LineaCatalogo.TECNOLOGIA);
    repositorio.conCategorias(proyectores);

    assertEquals(List.of(proyectores), new ListarCategorias(repositorio).ejecutar());
  }

  /**
   * Las subcategorías salen en la misma lista plana, con su padre puesto. Las cuelga quien pinta.
   */
  @Test
  void devuelveLaListaPlanaConElPadreDeCadaUna() {
    RepositorioCategoriasFalso repositorio = new RepositorioCategoriasFalso();
    Categoria dama = Categoria.crear("Dama", new Slug("ropa-dama"), LineaCatalogo.ROPA);
    Categoria faldas = Categoria.crearBajo(dama, "Faldas", new Slug("ropa-dama-faldas"));
    repositorio.conCategorias(dama, faldas);

    List<Categoria> resultado = new ListarCategorias(repositorio).ejecutar();

    assertEquals(2, resultado.size());
    assertTrue(resultado.contains(dama));
    assertEquals(dama.id(), faldas.padreId().orElseThrow());
  }
}
