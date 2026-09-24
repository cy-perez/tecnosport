package co.tecnosport.api.application.catalogo;

import co.tecnosport.api.domain.catalogo.Categoria;
import co.tecnosport.api.domain.compartido.Slug;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Puerto de categorías. Implementación de producción: JPA con PostgreSQL. */
public interface RepositorioCategorias {

  /**
   * Todas las categorías del árbol, de todos los niveles y sin filtrar por línea: quien llama
   * agrupa, cuelga o filtra si lo necesita.
   *
   * <p><b>Incluye las que no tienen ni un producto</b>, y desde el 24 de septiembre de 2026 eso
   * vale también para la vitrina. Hubo un {@code listarConProductosPublicados()} que las escondía
   * para no ofrecer un filtro que lleva a una rejilla vacía, y con el árbol dejó de tener sentido:
   * un menú que muestra "Ropa › Dama" pero se salta "Faldas" porque hoy no hay ninguna le está
   * diciendo al comprador que no vendemos faldas. La rejilla ya sabe decir que no encontró nada.
   */
  List<Categoria> listarTodas();

  Optional<Categoria> buscarPorId(UUID id);

  /** Para defender la unicidad del slug antes de escribir, que es lo que exige {@code V1}. */
  Optional<Categoria> buscarPorSlug(Slug slug);

  /** Las hijas directas. Vacío si es una hoja. */
  List<Categoria> hijasDe(UUID padreId);

  /** Inserta o actualiza. El id lo trae la categoría, así que no hacen falta dos métodos. */
  void guardar(Categoria categoria);

  /**
   * Borra una categoría. Quien llama comprueba antes que no tenga hijas ni productos: aquí abajo
   * solo queda la llave foránea, que falla con un error de base de datos y no con uno que el panel
   * pueda explicar.
   */
  void eliminar(UUID id);

  /**
   * ¿Cuelga algún producto de esta categoría, en cualquier estado? Mira también los borradores a
   * propósito: un borrador es trabajo de alguien, y borrarle la categoría por debajo lo rompe igual
   * que si estuviera publicado.
   */
  boolean tieneProductos(UUID categoriaId);
}
