package co.tecnosport.api.application.catalogo;

import co.tecnosport.api.domain.catalogo.Producto;
import java.util.Objects;
import java.util.UUID;

/**
 * Saca un producto de la vitrina y lo devuelve a {@code BORRADOR}.
 *
 * <p>{@code PublicarProducto} decía que este caso de uso no se añadía "por simetría", porque
 * retirar algo que ya se vendió tiene consecuencias que nadie había decidido. Se decidieron, con el
 * código a la vista, y por eso ahora existe:
 *
 * <ul>
 *   <li><b>La vitrina.</b> Desaparece de la rejilla y de la ficha: las dos consultas filtran por
 *       {@code estado = 'PUBLICADO'}. Un enlace compartido responde 404, que es lo que corresponde
 *       a algo retirado — no un producto fantasma que se puede seguir comprando.
 *   <li><b>El sitemap.</b> Sale solo en la siguiente generación, porque {@code
 *       listarProductosPublicados} tiene el mismo filtro.
 *   <li><b>Los pedidos en curso.</b> No se tocan. Llevan sus líneas congeladas y ningún paso
 *       posterior —despacho, entrega, recaudo— vuelve a mirar el estado del producto. Retirar de la
 *       vitrina no es cancelar lo vendido, y confundir las dos cosas sería el error caro.
 *   <li><b>Los carritos.</b> La línea se queda ahí; el checkout la rechaza al crear el pedido
 *       ({@code CrearPedido} exige {@code PUBLICADO} y responde como si la variante no existiera).
 *       Queda dicho que el mensaje es el de "ya no está disponible" y no uno propio: quien lo
 *       mejore, que lo haga sabiendo que también cubre la variante borrada.
 * </ul>
 *
 * <p>Idempotente, como su inverso: despublicar un borrador no es un error, es el estado que se
 * pedía.
 */
public final class DespublicarProducto {

  private final RepositorioProductos repositorioProductos;

  public DespublicarProducto(RepositorioProductos repositorioProductos) {
    this.repositorioProductos =
        Objects.requireNonNull(
            repositorioProductos, "El repositorio de productos no puede ser nulo.");
  }

  public Producto ejecutar(UUID productoId) {
    Objects.requireNonNull(productoId, "El id del producto no puede ser nulo.");

    Producto producto =
        repositorioProductos
            .buscarPorId(productoId)
            .orElseThrow(() -> new ProductoNoEncontradoPorIdException(productoId));

    producto.despublicar();
    repositorioProductos.actualizar(producto);
    return producto;
  }
}
