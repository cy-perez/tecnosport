package co.tecnosport.api.application.catalogo;

import co.tecnosport.api.domain.catalogo.ImagenProducto;
import co.tecnosport.api.domain.catalogo.IntercambioDePrincipal;
import co.tecnosport.api.domain.catalogo.Producto;
import java.util.Objects;

/**
 * Usa una foto de la galería como principal, sin volver a subir nada. Es lo que la revisión de un
 * borrador hace al marcar la foto principal, sobre un producto que ya existe.
 *
 * <p><b>No toca el bucket.</b> La principal anterior pasa a la galería con sus mismos objetos, y la
 * nueva principal sigue apuntando a los de la galería. Por eso {@link ConfirmarImagenPrincipal} ya
 * no puede barrer el prefijo {@code principal-} a ciegas: ahí puede haber una foto viva de la
 * galería.
 */
public final class UsarImagenDeGaleriaComoPrincipal {

  private final RepositorioProductos repositorioProductos;

  public UsarImagenDeGaleriaComoPrincipal(RepositorioProductos repositorioProductos) {
    this.repositorioProductos = Objects.requireNonNull(repositorioProductos);
  }

  public ImagenProducto ejecutar(UsarImagenDeGaleriaComoPrincipalComando comando) {
    Objects.requireNonNull(comando, "El comando no puede ser nulo.");
    Producto producto =
        repositorioProductos
            .buscarPorId(comando.productoId())
            .orElseThrow(() -> new ProductoNoEncontradoPorIdException(comando.productoId()));
    IntercambioDePrincipal intercambio =
        producto.usarImagenDeGaleriaComoPrincipal(comando.imagenId());
    repositorioProductos.guardarIntercambioDePrincipal(producto.id(), intercambio);
    return intercambio.nuevaPrincipal();
  }
}
