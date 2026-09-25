package co.tecnosport.api.application.catalogo;

import co.tecnosport.api.application.pedido.RepositorioPedidos;
import co.tecnosport.api.domain.catalogo.EstadoProducto;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.catalogo.Variante;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Borra un producto del catálogo, con todo lo que cuelga de él, y solo si no arrastra nada.
 *
 * <p><b>Dos comprobaciones, y las dos son lecturas previas</b>, igual que en {@link
 * EliminarCategoria} y por el mismo motivo: la diferencia entre un mensaje que el panel puede
 * explicar —"tiene ventas"— y un {@code 500} con una violación de integridad dentro. Aquí hay una
 * razón de más para no confiar en la base: {@code linea_pedido.variante_id} <b>no tiene llave
 * foránea</b> (guarda una copia congelada de la venta, ver {@code V4__pedido.sql}), así que la base
 * no rechazaría nada. Si esta clase no pregunta, nadie pregunta.
 *
 * <ul>
 *   <li><b>Publicado, no.</b> Hay que retirarlo primero: ver {@link ProductoPublicadoException}.
 *   <li><b>Con ventas, no.</b> Nunca, ni retirado: ver {@link ProductoConVentasException}.
 * </ul>
 *
 * <p><b>Los objetos del bucket se borran antes que las filas</b>, que es el orden de {@code
 * EliminarSetRotacion} y el contrario al de {@code QuitarImagenDeGaleria}. Al revés —filas primero,
 * objetos después— un fallo a mitad deja los objetos huérfanos para siempre: ya no hay ninguna fila
 * que diga qué prefijo había que limpiar, y {@code npm run huerfanos} sería la única forma de
 * enterarse. Así, un fallo deja un producto sin fotos y todavía en la base, y reintentar termina el
 * trabajo, porque borrar por prefijo es idempotente.
 *
 * <p>Un solo prefijo para las tres clases de imagen: la principal, la galería y los fotogramas de
 * rotación viven todos bajo {@code productos/{id}/} ({@code ClavesDePrincipal}, {@code
 * ClavesDeGaleria}, {@code ClavesDeRotacion}). Recorrer los sets de rotación uno por uno dejaría
 * fuera justo lo que interesa reclamar: los objetos de una subida que murió a mitad y nunca llegó a
 * ser una fila.
 *
 * <p>Y como en {@code EliminarSetRotacion}: <b>esto sigue siendo destructivo, y lo que lo hace
 * aceptable es el versionado de objetos del bucket</b> (docs/07-infra-gcp.md). Las filas no tienen
 * esa red.
 */
public final class EliminarProducto {

  private final RepositorioProductos repositorioProductos;
  private final RepositorioPedidos repositorioPedidos;
  private final AlmacenDeImagenes almacenDeImagenes;

  public EliminarProducto(
      RepositorioProductos repositorioProductos,
      RepositorioPedidos repositorioPedidos,
      AlmacenDeImagenes almacenDeImagenes) {
    this.repositorioProductos =
        Objects.requireNonNull(
            repositorioProductos, "El repositorio de productos no puede ser nulo.");
    this.repositorioPedidos =
        Objects.requireNonNull(repositorioPedidos, "El repositorio de pedidos no puede ser nulo.");
    this.almacenDeImagenes =
        Objects.requireNonNull(almacenDeImagenes, "El almacén de imágenes no puede ser nulo.");
  }

  /** Devuelve cuántos objetos se borraron del bucket, para que quien llame lo registre. */
  public int ejecutar(UUID productoId) {
    Objects.requireNonNull(productoId, "El id del producto no puede ser nulo.");

    Producto producto =
        repositorioProductos
            .buscarPorId(productoId)
            .orElseThrow(() -> new ProductoNoEncontradoPorIdException(productoId));

    if (producto.estado() == EstadoProducto.PUBLICADO) {
      throw new ProductoPublicadoException(producto.nombre());
    }

    List<UUID> variantes = producto.variantes().stream().map(Variante::id).toList();
    if (!variantes.isEmpty() && repositorioPedidos.hayLineasDeAlgunaVariante(variantes)) {
      throw new ProductoConVentasException(producto.nombre());
    }

    int borrados = almacenDeImagenes.eliminarPorPrefijo(prefijoDe(productoId), Set.of());
    repositorioProductos.eliminar(productoId);
    return borrados;
  }

  /** Todo lo que un producto ocupa en el bucket cuelga de aquí. */
  private static String prefijoDe(UUID productoId) {
    return "productos/" + productoId + "/";
  }
}
