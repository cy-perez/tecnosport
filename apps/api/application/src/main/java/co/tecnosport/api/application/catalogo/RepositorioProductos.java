package co.tecnosport.api.application.catalogo;

import co.tecnosport.api.application.compartido.ResultadoPaginado;
import co.tecnosport.api.domain.catalogo.ImagenProducto;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.catalogo.Variante;
import co.tecnosport.api.domain.compartido.Sku;
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

  /**
   * Inserta un producto nuevo. Sin variantes ni imágenes todavía — eso es de un caso de uso propio.
   */
  void guardar(Producto producto);

  /**
   * Búsqueda por id para el panel admin, sin filtrar por estado — igual criterio que {@link
   * #buscarPorSlug}, ve productos en cualquier estado.
   */
  Optional<Producto> buscarPorId(UUID id);

  /** Actualiza un producto existente. A diferencia de {@link #guardar}, no es una inserción. */
  void actualizar(Producto producto);

  /**
   * Persiste una variante nueva (con sus atributos) para un producto ya existente. Quien llama debe
   * haber validado antes que el producto existe y que el SKU no está en uso.
   */
  void agregarVariante(UUID productoId, Variante variante);

  /**
   * El SKU es único en todo el catálogo, no solo dentro del producto que se está armando en memoria
   * — {@code Producto.agregarVariante} no puede ver esto por sí solo.
   */
  boolean existeVarianteConSku(Sku sku);

  /**
   * Reemplaza la imagen principal del producto (a lo sumo una por producto, constraint única en BD)
   * — si había una anterior, esta la sustituye.
   */
  void guardarImagenPrincipal(UUID productoId, ImagenProducto imagen);
}
