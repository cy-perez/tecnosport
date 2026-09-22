package co.tecnosport.api.presentation.catalogo;

import co.tecnosport.api.application.catalogo.ProductosPaginados;
import co.tecnosport.api.domain.catalogo.ImagenProducto;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.presentation.catalogo.dto.ImagenDeGaleriaRespuesta;
import co.tecnosport.api.presentation.catalogo.dto.ImagenRespuesta;
import co.tecnosport.api.presentation.catalogo.dto.ProductoAdminDetalleRespuesta;
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
        producto.descripcion(),
        producto.slug().valor(),
        producto.estado().name(),
        mapeadorCatalogo.aRespuesta(producto.marca()),
        mapeadorCatalogo.aRespuesta(producto.categoria()),
        producto.imagenPrincipal().map(imagen -> imagen.url()).orElse(null),
        producto.variantes().size());
  }

  public ProductoAdminDetalleRespuesta aDetalle(Producto producto) {
    return new ProductoAdminDetalleRespuesta(
        producto.id(),
        producto.nombre(),
        producto.descripcion(),
        producto.slug().valor(),
        producto.estado().name(),
        mapeadorCatalogo.aRespuesta(producto.marca()),
        mapeadorCatalogo.aRespuesta(producto.categoria()),
        producto.imagenPrincipal().map(ImagenProducto::url).orElse(null),
        producto.variantes().size(),
        producto.galeria().stream().map(this::aRespuestaDeGaleria).toList());
  }

  public ImagenRespuesta aRespuesta(ImagenProducto imagen) {
    return mapeadorCatalogo.aRespuesta(imagen);
  }

  public ImagenDeGaleriaRespuesta aRespuestaDeGaleria(ImagenProducto imagen) {
    return new ImagenDeGaleriaRespuesta(
        imagen.id(),
        imagen.url(),
        imagen.url(),
        imagen.ancho(),
        imagen.alto(),
        imagen.orden(),
        imagen.altEs(),
        imagen.altEn());
  }

  public ProductosAdminPaginadosRespuesta aRespuesta(ProductosPaginados productos) {
    return new ProductosAdminPaginadosRespuesta(
        productos.items().stream().map(this::aRespuesta).toList(),
        productos.pagina(),
        productos.totalPaginas(),
        productos.totalProductos());
  }
}
