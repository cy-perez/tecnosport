package co.tecnosport.api.application.catalogo;

import co.tecnosport.api.domain.catalogo.Categoria;
import java.util.List;
import java.util.Objects;

/**
 * El árbol de categorías completo, para el menú del sitio, el filtro de la vitrina y el panel.
 *
 * <p><b>Devuelve también las vacías</b>, y eso es un cambio del 24 de septiembre de 2026. Antes
 * llamaba a {@code listarConProductosPublicados()} para no ofrecer un filtro que lleva a una
 * rejilla en blanco, y el argumento era bueno mientras el catálogo era una lista plana. Con el
 * árbol dejó de serlo: el menú pinta "Ropa › Dama" con sus nueve prendas, y saltarse "Faldas"
 * porque hoy no hay ninguna le dice al comprador que no vendemos faldas, que es una afirmación más
 * cara que una rejilla vacía. La rejilla ya sabe decir que no encontró nada con esos filtros.
 *
 * <p>Con eso desapareció la razón de ser de {@code ListarCategoriasAdmin}, que existía solo para
 * que el panel viera lo que la vitrina escondía. Un caso de uso menos.
 *
 * <p>Sin ordenar por jerarquía: devuelve la lista plana ordenada por nombre y quien la recibe la
 * cuelga. Armar el árbol aquí obligaría a inventar un tipo "nodo" en {@code application} que solo
 * sirve para viajar, y el frontend lo tendría que deshacer para pintarlo.
 */
public final class ListarCategorias {

  private final RepositorioCategorias repositorioCategorias;

  public ListarCategorias(RepositorioCategorias repositorioCategorias) {
    this.repositorioCategorias =
        Objects.requireNonNull(
            repositorioCategorias, "El repositorio de categorías no puede ser nulo.");
  }

  public List<Categoria> ejecutar() {
    return repositorioCategorias.listarTodas();
  }
}
