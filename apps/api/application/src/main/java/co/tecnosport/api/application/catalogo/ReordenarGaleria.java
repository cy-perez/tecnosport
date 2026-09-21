package co.tecnosport.api.application.catalogo;

import co.tecnosport.api.domain.catalogo.ImagenProducto;
import co.tecnosport.api.domain.catalogo.Producto;
import java.util.List;
import java.util.Objects;

/**
 * Deja la galería de un producto en el orden pedido.
 *
 * <p><b>El caso de uso más corto de los cuatro de la galería, y eso es lo que tiene que ser.</b>
 * Agregar y quitar hablan con el bucket; este no toca ni un objeto, porque reordenar no cambia
 * ningún archivo: cambia en qué posición se pinta cada uno. Si alguna vez este caso de uso necesita
 * el almacén, algo se entendió mal.
 *
 * <p>Quien decide si el orden pedido tiene sentido es el agregado —{@code
 * Producto.reordenarGaleria} exige la galería entera, sin repetir ninguna y sin dejarse ninguna—, y
 * lo que sale de aquí ya viene renumerado de 0 a n-1.
 */
public final class ReordenarGaleria {

  private final RepositorioProductos repositorioProductos;

  public ReordenarGaleria(RepositorioProductos repositorioProductos) {
    this.repositorioProductos = Objects.requireNonNull(repositorioProductos);
  }

  public List<ImagenProducto> ejecutar(ReordenarGaleriaComando comando) {
    Objects.requireNonNull(comando, "El comando no puede ser nulo.");

    Producto producto =
        repositorioProductos
            .buscarPorId(comando.productoId())
            .orElseThrow(() -> new ProductoNoEncontradoPorIdException(comando.productoId()));

    producto.reordenarGaleria(comando.imagenIds());
    repositorioProductos.guardarOrdenDeGaleria(producto.id(), producto.galeria());
    return producto.galeria();
  }
}
