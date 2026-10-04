package co.tecnosport.api.application.proveedores;

import co.tecnosport.api.domain.proveedores.LoteIngesta;
import java.util.Objects;
import java.util.UUID;

/**
 * Borra una ingesta con todo lo que trajo: sus mensajes, publicaciones y borradores, el ZIP y las
 * fotos del bucket privado, y los productos que salieron de ella <b>mientras no estén
 * publicados</b>.
 *
 * <p><b>Un producto publicado o vendido se queda</b> en el catálogo, tal cual: borrar la ingesta no
 * puede sacar algo de la tienda ni dejar un pedido sin producto. Lo que pierde es su borrador, y
 * con él la huella visual con que la ingesta reconocía una foto suya que volviera con otro texto
 * ({@link RepositorioBorradores#huellasVisualesDelProveedor}); el producto conserva su huella de
 * texto, que es la que reconoce una renovación.
 *
 * <p>Solo cuentan los productos que nacieron de un borrador aprobado de este lote. Uno que una
 * renovación de este lote actualizó nació en otro y no se toca.
 *
 * <p>Como en {@link EliminarProveedor}: con el lote en curso, no; y los objetos del bucket se
 * borran antes que las filas, porque al revés un fallo a mitad deja objetos que ya ninguna fila
 * nombra. Borrar los mensajes tiene la consecuencia que explica {@link EliminarBorrador}: volver a
 * subir la misma exportación la vuelve a convertir en borradores. Es lo que se pide.
 *
 * <p><b>Los objetos del bucket se borran dentro de la transacción del controlador</b>, como en
 * {@link EliminarProveedor}: si algo falla después, las filas vuelven y los archivos ya no, y
 * reintentar termina el trabajo porque borrar un objeto que no está no falla. Con lotes de cientos
 * de fotos eso alarga la transacción; si llega a pesar, el borrado de objetos sale de ella.
 *
 * <p>Una aprobación de un borrador de este lote que confirme mientras tanto puede dejar su producto
 * fuera de la cuenta: se lee antes de que exista. Es una carrera de dos personas sobre el mismo
 * lote en el mismo segundo, y el producto queda en borrador, visible en el panel.
 */
public final class EliminarLoteDeIngesta {

  private final RepositorioLotesIngesta repositorioLotes;
  private final EliminacionDeProductos eliminacionDeProductos;
  private final AlmacenDeArchivosDeProveedor almacen;

  public EliminarLoteDeIngesta(
      RepositorioLotesIngesta repositorioLotes,
      EliminacionDeProductos eliminacionDeProductos,
      AlmacenDeArchivosDeProveedor almacen) {
    this.repositorioLotes = Objects.requireNonNull(repositorioLotes);
    this.eliminacionDeProductos = Objects.requireNonNull(eliminacionDeProductos);
    this.almacen = Objects.requireNonNull(almacen);
  }

  public LoteEliminado ejecutar(UUID loteId) {
    Objects.requireNonNull(loteId, "El id no puede ser nulo.");
    LoteIngesta lote =
        repositorioLotes
            .buscarPorId(loteId)
            .orElseThrow(() -> new LoteNoEncontradoException(loteId));
    if (lote.estaAbierto()) {
      throw new LoteEnCursoException();
    }
    DependenciasDeLote dependencias = repositorioLotes.dependenciasDe(loteId);

    int eliminados = 0;
    int conservados = 0;
    for (UUID productoId : dependencias.productos()) {
      if (eliminacionDeProductos.eliminarSiSePuede(productoId)) {
        eliminados++;
      } else {
        conservados++;
      }
    }
    for (String archivo : dependencias.archivos()) {
      almacen.borrar(archivo);
    }
    repositorioLotes.eliminarConSuHistorial(loteId);
    return new LoteEliminado(eliminados, conservados, dependencias.archivos().size());
  }
}
