package co.tecnosport.api.application.proveedores;

import co.tecnosport.api.domain.proveedores.BorradorProducto;
import co.tecnosport.api.domain.proveedores.EstadoBorrador;
import co.tecnosport.api.domain.proveedores.PublicacionProveedor;
import java.util.Objects;
import java.util.UUID;

/**
 * Saca una foto de la revisión de un borrador: deja de verse y no se puede aprobar con ella. El
 * archivo se queda en el bucket privado porque es de la publicación —otro borrador del mismo
 * mensaje puede usarlo—, y se va con el borrador cuando este se borra.
 */
public final class DescartarFotoDeBorrador {

  private final RepositorioBorradores repositorioBorradores;
  private final RepositorioPublicacionesProveedor repositorioPublicaciones;

  public DescartarFotoDeBorrador(
      RepositorioBorradores repositorioBorradores,
      RepositorioPublicacionesProveedor repositorioPublicaciones) {
    this.repositorioBorradores = Objects.requireNonNull(repositorioBorradores);
    this.repositorioPublicaciones = Objects.requireNonNull(repositorioPublicaciones);
  }

  public BorradorProducto ejecutar(UUID borradorId, UUID mensajeId) {
    BorradorProducto borrador =
        repositorioBorradores
            .buscarPorId(borradorId)
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
    if (!publicacion.medios().contains(mensajeId)) {
      throw new FotoNoEsDelBorradorException(mensajeId);
    }
    borrador.descartarFoto(mensajeId);
    // La huella visual sale de la primera foto de la publicación (ResolverBorrador.pHashDe).
    if (publicacion.medios().getFirst().equals(mensajeId)) {
      borrador.olvidarHuellaVisual();
    }
    repositorioBorradores.actualizar(borrador);
    return borrador;
  }
}
