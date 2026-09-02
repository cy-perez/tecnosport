package co.tecnosport.api.application.catalogo;

import co.tecnosport.api.application.compartido.ResultadoPaginado;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.compartido.Slug;
import java.util.Optional;

/** Puerto del catálogo. Implementación de producción: JPA con PostgreSQL. */
public interface RepositorioProductos {

  /**
   * Búsqueda paginada para la vitrina pública. La implementación debe filtrar por {@code estado ==
   * PUBLICADO} en la propia consulta: no se puede paginar correctamente trayendo todo y filtrando
   * después.
   */
  ResultadoPaginado<Producto> buscar(
      FiltroProductos filtro, OrdenProductos orden, String cursor, int tamanoPagina);

  /**
   * Búsqueda simple por slug, sin filtrar por estado: devuelve el producto exista o no esté
   * publicado. La visibilidad pública la decide quien llama (ver {@code VerFichaDeProducto}), no
   * este puerto.
   */
  Optional<Producto> buscarPorSlug(Slug slug);
}
