package co.tecnosport.api.application.catalogo;

import co.tecnosport.api.application.inventario.RepositorioInventario;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.catalogo.Variante;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Borra de un golpe los productos que no están publicados, cada uno con {@link EliminarProducto}:
 * sus variantes, su inventario y todas sus fotos del bucket.
 *
 * <p><b>No publicado quiere decir {@code BORRADOR}</b>, y eso junta tres cosas distintas: lo creado
 * a mano que nunca se publicó, lo que se retiró de la vitrina, y lo que se aprobó desde una lista
 * de tecnología y espera sus fotos. Un producto publicado pero oculto por disponibilidad no entra:
 * está publicado, y vuelve solo a la vitrina cuando el proveedor lo repone.
 *
 * <p><b>Dos cosas lo conservan</b>, y se cuentan aparte para decirlo sin tumbar la tanda:
 *
 * <ul>
 *   <li><b>Ventas</b>, por la misma razón que en {@link EliminarProducto}: la garantía y el
 *       retracto buscan el producto por la variante vendida.
 *   <li><b>Existencias</b>: un producto retirado de la vitrina para rehacer sus fotos puede tener
 *       unidades en bodega, y borrarlo se llevaría el libro de inventario entero —entradas,
 *       ajustes—. Uno por uno sí se puede, mirándolo; en bloque, no.
 * </ul>
 *
 * <p><b>Por tandas con cursor, y con tope.</b> Cada producto puede tener cientos de objetos —tres
 * resoluciones por foto y los fotogramas del visor 360—, y el bucket se borra uno por uno. La tanda
 * corre en la transacción del controlador, como en {@code EliminarLoteDeIngesta}: si algo falla,
 * las filas vuelven y los objetos ya no, y repetir termina el trabajo porque borrar por prefijo es
 * idempotente. El cursor —y no una página— deja atrás a los conservados. El tope se fija en la
 * primera tanda con el último id que había: los ids son UUID v7 y crecen con el tiempo, así que sin
 * él un producto que alguien crea en otra pestaña mientras corre el borrado caería en una tanda
 * posterior. La pregunta dijo cuántos eran; se borran esos.
 *
 * <p>Un producto que alguien publica o borra entre la lista y su turno se salta.
 */
public final class EliminarProductosNoPublicados {

  private final ProductosNoPublicados productosNoPublicados;
  private final RepositorioProductos repositorioProductos;
  private final RepositorioInventario repositorioInventario;
  private final EliminarProducto eliminarProducto;

  public EliminarProductosNoPublicados(
      ProductosNoPublicados productosNoPublicados,
      RepositorioProductos repositorioProductos,
      RepositorioInventario repositorioInventario,
      EliminarProducto eliminarProducto) {
    this.productosNoPublicados = Objects.requireNonNull(productosNoPublicados);
    this.repositorioProductos = Objects.requireNonNull(repositorioProductos);
    this.repositorioInventario = Objects.requireNonNull(repositorioInventario);
    this.eliminarProducto = Objects.requireNonNull(eliminarProducto);
  }

  /** Cuántos hay en borrador, contando los que se quedarían por ventas o por existencias. */
  public long contar() {
    return productosNoPublicados.contar();
  }

  /**
   * @param desde el cursor que devolvió la tanda anterior; nulo para empezar
   * @param hasta el tope que devolvió la primera tanda; nulo para empezar, y entonces se fija
   */
  public ProductosEliminados ejecutar(UUID desde, UUID hasta, int tamanoTanda) {
    if (tamanoTanda < 1) {
      throw new IllegalArgumentException("La tanda tiene que tener al menos un producto.");
    }
    Optional<UUID> tope = hasta != null ? Optional.of(hasta) : productosNoPublicados.ultimo();
    if (tope.isEmpty()) {
      return new ProductosEliminados(0, 0, 0, 0, null, null);
    }
    List<UUID> ids = productosNoPublicados.ids(desde, tope.get(), tamanoTanda);
    int eliminados = 0;
    int conVentas = 0;
    int conExistencias = 0;
    int objetos = 0;
    for (UUID id : ids) {
      if (tieneExistencias(id)) {
        conExistencias++;
        continue;
      }
      try {
        objetos += eliminarProducto.ejecutar(id);
        eliminados++;
      } catch (ProductoConVentasException e) {
        conVentas++;
      } catch (ProductoPublicadoException | ProductoNoEncontradoPorIdException e) {
        // Lo publicaron o lo borraron mientras tanto: ya no es de los que se querían borrar.
      }
    }
    UUID siguiente = ids.size() < tamanoTanda ? null : ids.getLast();
    return new ProductosEliminados(
        eliminados, conVentas, conExistencias, objetos, siguiente, tope.get());
  }

  /** Alguna variante con unidades en el libro, reservadas o no. Un producto que no existe, no. */
  private boolean tieneExistencias(UUID productoId) {
    List<UUID> variantes =
        repositorioProductos
            .buscarPorId(productoId)
            .map(Producto::variantes)
            .orElse(List.of())
            .stream()
            .map(Variante::id)
            .toList();
    return !variantes.isEmpty()
        && repositorioInventario.buscarPorVarianteIds(variantes).stream()
            .anyMatch(inventario -> inventario.saldoTotal() > 0);
  }
}
