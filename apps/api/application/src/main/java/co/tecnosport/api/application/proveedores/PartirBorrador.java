package co.tecnosport.api.application.proveedores;

import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.domain.proveedores.BorradorProducto;
import co.tecnosport.api.domain.proveedores.EstadoBorrador;
import co.tecnosport.api.domain.proveedores.PublicacionProveedor;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Parte un borrador en dos desde el panel: las fotos que se eligen se van a un borrador nuevo de la
 * misma publicación ({@link BorradorProducto#partir}). Es la salida de emergencia cuando la lectura
 * de fotos juntó dos productos en uno, o no supo repartir las fotos de un conjunto.
 *
 * <p>La transacción la abre quien llama: escribe dos borradores, y los dos o ninguno.
 */
public final class PartirBorrador {

  private final RepositorioBorradores repositorioBorradores;
  private final RepositorioPublicacionesProveedor repositorioPublicaciones;
  private final Reloj reloj;

  public PartirBorrador(
      RepositorioBorradores repositorioBorradores,
      RepositorioPublicacionesProveedor repositorioPublicaciones,
      Reloj reloj) {
    this.repositorioBorradores = Objects.requireNonNull(repositorioBorradores);
    this.repositorioPublicaciones = Objects.requireNonNull(repositorioPublicaciones);
    this.reloj = Objects.requireNonNull(reloj);
  }

  /**
   * @param fotos las de la publicación que se lleva el borrador nuevo
   * @return el borrador nuevo
   * @throws FotoNoEsDelBorradorException si alguna no es una foto de la publicación
   */
  public BorradorProducto ejecutar(UUID borradorId, List<UUID> fotos) {
    Objects.requireNonNull(fotos, "Partir dice qué fotos se van.");
    BorradorProducto borrador =
        repositorioBorradores
            .buscarPorIdParaActualizar(borradorId)
            .orElseThrow(() -> new BorradorNoEncontradoException(borradorId));
    if (borrador.estado() != EstadoBorrador.EN_REVISION) {
      throw new BorradorNoEditableException(borrador.estado());
    }
    PublicacionProveedor publicacion =
        repositorioPublicaciones
            .buscarPorId(borrador.publicacionId())
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "El borrador apunta a una publicación que no existe."));
    for (UUID foto : fotos) {
      if (!publicacion.medios().contains(foto)) {
        throw new FotoNoEsDelBorradorException(foto);
      }
    }
    BorradorProducto nuevo = borrador.partir(fotos, publicacion.medios(), reloj.ahora());
    repositorioBorradores.actualizar(borrador);
    repositorioBorradores.guardar(nuevo);
    return nuevo;
  }
}
