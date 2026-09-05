package co.tecnosport.api.presentation.catalogo;

import co.tecnosport.api.application.catalogo.ProductosPaginados;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.presentation.catalogo.dto.ProductoAdminRespuesta;
import co.tecnosport.api.presentation.catalogo.dto.ProductosAdminPaginadosRespuesta;
import org.springframework.stereotype.Component;

/**
 * Domain -> DTO del panel admin. Separado de {@link MapeadorRespuestasCatalogo} (vitrina pública)
 * porque las reglas de exposición son distintas: aquí sí se expone {@code id} y {@code estado}, y
 * un producto en {@code BORRADOR} se ve igual que uno {@code PUBLICADO}. Lista deliberadamente
 * liviana, sin variantes completas ni rotación — eso es para la pantalla de detalle/edición de un
 * caso de uso futuro.
 */
@Component
public class MapeadorRespuestasProductoAdmin {

  private final MapeadorRespuestasCatalogo mapeadorCatalogo;

  public MapeadorRespuestasProductoAdmin(MapeadorRespuestasCatalogo mapeadorCatalogo) {
    this.mapeadorCatalogo = mapeadorCatalogo;
  }

  public ProductoAdminRespuesta aRespuesta(Producto producto) {
    return new ProductoAdminRespuesta(
        producto.id(),
        producto.nombre(),
        producto.slug().valor(),
        producto.estado().name(),
        mapeadorCatalogo.aRespuesta(producto.marca()),
        mapeadorCatalogo.aRespuesta(producto.categoria()),
        producto.imagenPrincipal().map(imagen -> imagen.url()).orElse(null),
        producto.variantes().size());
  }

  public ProductosAdminPaginadosRespuesta aRespuesta(ProductosPaginados productos) {
    return new ProductosAdminPaginadosRespuesta(
        productos.items().stream().map(this::aRespuesta).toList(),
        productos.pagina(),
        productos.totalPaginas(),
        productos.totalProductos());
  }
}
