package co.tecnosport.api.application.catalogo;

import co.tecnosport.api.domain.catalogo.Producto;
import java.util.Objects;
import java.util.UUID;

/**
 * El paso que faltaba: un producto pasa de {@code BORRADOR} a {@code PUBLICADO} y desde ese momento
 * existe para quien compra.
 *
 * <p>Nació el 19 de septiembre de 2026, al ir a cargar el primer catálogo real, y lo que se
 * encontró conviene que quede escrito: {@code Producto.publicar()} <b>existía desde la Fase 1</b>,
 * con su invariante y todo — no se publica sin imagen principal —, y <b>solo lo llamaban las
 * pruebas</b>. Ningún caso de uso, ningún endpoint, ningún botón. El sembrador no lo necesita
 * porque escribe el estado directo en la fila, así que la tienda de desarrollo siempre se vio llena
 * y nadie notó que por el panel no había forma de publicar nada.
 *
 * <p>Es el mismo género que el plugin de capas que aceptaba la configuración sin aplicarla: una
 * regla escrita, correcta, y fuera del alcance de todo lo que corre en producción. La diferencia es
 * que aquella no protegía; esta directamente impedía usar el panel para lo que se construyó.
 *
 * <p>Durante un día no hubo {@code DespublicarProducto}, y este javadoc explicaba por qué: retirar
 * algo que ya se vendió tiene consecuencias que nadie había decidido —los pedidos en curso, los
 * enlaces compartidos, el sitemap ya indexado— y escribirlo sin decidirlas las habría decidido en
 * silencio. **Ya existe**, con esas tres respuestas escritas en su propio javadoc y comprobadas
 * contra el código, no supuestas.
 */
public final class PublicarProducto {

  private final RepositorioProductos repositorioProductos;

  public PublicarProducto(RepositorioProductos repositorioProductos) {
    this.repositorioProductos =
        Objects.requireNonNull(
            repositorioProductos, "El repositorio de productos no puede ser nulo.");
  }

  /**
   * Publicar un producto ya publicado no es un error: el resultado es el que se pedía. Se deja
   * idempotente a propósito, porque el panel puede reintentar y porque un 409 aquí obligaría a
   * quien llama a consultar antes para no chocar.
   */
  public Producto ejecutar(UUID productoId) {
    Objects.requireNonNull(productoId, "El id del producto no puede ser nulo.");

    Producto producto =
        repositorioProductos
            .buscarPorId(productoId)
            .orElseThrow(() -> new ProductoNoEncontradoPorIdException(productoId));

    producto.publicar();
    repositorioProductos.actualizar(producto);
    return producto;
  }
}
