package co.tecnosport.api.application.catalogo;

import co.tecnosport.api.application.compartido.ResultadoPaginado;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.compartido.Slug;
import java.util.Optional;
import java.util.UUID;

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

  /**
   * El producto dueño de una variante, para revalidar precio y existencia al crear un pedido
   * (docs/03-api.md): el cliente solo envía el id de la variante, nunca su precio. Sin filtrar por
   * estado, igual que {@link #buscarPorSlug}.
   */
  Optional<Producto> buscarPorVarianteId(UUID varianteId);

  /**
   * Listado del panel admin: todos los estados (a diferencia de {@link #buscar}, que solo trae
   * {@code PUBLICADO}), paginado por página y ordenado por más reciente primero.
   */
  ProductosPaginados buscarParaAdmin(int pagina, int tamanoPagina);

  /** Inserta un producto nuevo. Sin variantes ni imágenes todavía — eso es de un caso de uso propio. */
  void guardar(Producto producto);
}
