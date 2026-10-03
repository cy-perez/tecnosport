package co.tecnosport.api.application.catalogo;

import co.tecnosport.api.domain.catalogo.ImagenProducto;
import co.tecnosport.api.domain.catalogo.Producto;
import java.util.Objects;

/**
 * Marca de qué color es una foto de la galería, colgándola de una variante de ese color —o de
 * ninguna—. Lo mismo que la revisión de un borrador hace al aprobar, pero sobre un producto que ya
 * existe: así las tarjetas de los dos caminos se comportan igual.
 */
public final class AsignarColorAImagenDeGaleria {

  private final RepositorioProductos repositorioProductos;

  public AsignarColorAImagenDeGaleria(RepositorioProductos repositorioProductos) {
    this.repositorioProductos = Objects.requireNonNull(repositorioProductos);
  }

  public ImagenProducto ejecutar(AsignarColorAImagenDeGaleriaComando comando) {
    Objects.requireNonNull(comando, "El comando no puede ser nulo.");
    Producto producto =
        repositorioProductos
            .buscarPorId(comando.productoId())
            .orElseThrow(() -> new ProductoNoEncontradoPorIdException(comando.productoId()));
    ImagenProducto marcada =
        producto.asignarVarianteAImagenDeGaleria(comando.imagenId(), comando.varianteId());
    repositorioProductos.guardarVarianteDeImagen(marcada.id(), marcada.varianteId().orElse(null));
    return marcada;
  }
}
