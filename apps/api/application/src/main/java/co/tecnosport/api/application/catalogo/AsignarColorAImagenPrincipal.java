package co.tecnosport.api.application.catalogo;

import co.tecnosport.api.domain.catalogo.ImagenProducto;
import co.tecnosport.api.domain.catalogo.Producto;
import java.util.Objects;

/**
 * Marca de qué color es la foto principal, como {@link AsignarColorAImagenDeGaleria} hace con las
 * de la galería. Hasta el 8 de octubre de 2026 solo la aprobación de un borrador podía dárselo, y
 * una principal subida desde el panel quedaba sin color y fuera de la galería de la ficha.
 */
public final class AsignarColorAImagenPrincipal {

  private final RepositorioProductos repositorioProductos;

  public AsignarColorAImagenPrincipal(RepositorioProductos repositorioProductos) {
    this.repositorioProductos = Objects.requireNonNull(repositorioProductos);
  }

  public ImagenProducto ejecutar(AsignarColorAImagenPrincipalComando comando) {
    Objects.requireNonNull(comando, "El comando no puede ser nulo.");
    Producto producto =
        repositorioProductos
            .buscarPorId(comando.productoId())
            .orElseThrow(() -> new ProductoNoEncontradoPorIdException(comando.productoId()));
    ImagenProducto marcada = producto.asignarVarianteAImagenPrincipal(comando.varianteId());
    repositorioProductos.guardarVarianteDeImagen(marcada.id(), marcada.varianteId().orElse(null));
    return marcada;
  }
}
