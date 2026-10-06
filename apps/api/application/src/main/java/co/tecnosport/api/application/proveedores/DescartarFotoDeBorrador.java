package co.tecnosport.api.application.proveedores;

import co.tecnosport.api.domain.proveedores.BorradorProducto;
import co.tecnosport.api.domain.proveedores.EstadoBorrador;
import co.tecnosport.api.domain.proveedores.FotoSubida;
import co.tecnosport.api.domain.proveedores.PublicacionProveedor;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Saca una foto de la revisión de un borrador: deja de verse y no se puede aprobar con ella. El
 * archivo se queda en el bucket privado porque es de la publicación —otro borrador del mismo
 * mensaje puede usarlo—, y se va con el borrador cuando este se borra.
 *
 * <p>Si con eso no queda ninguna foto con archivo, el borrador vuelve a llevar la alerta {@code
 * SIN_FOTOS}: la que quitó subir la primera, o la que tenga que aparecer por primera vez.
 *
 * <p>Una foto que se subió desde el panel es otra cosa: es solo de este borrador, así que se quita
 * y su archivo se borra. Primero el archivo, por la razón de {@link EliminarBorrador}: un fallo a
 * mitad deja una fila que apunta a nada, y reintentar termina el trabajo.
 */
public final class DescartarFotoDeBorrador {

  private final RepositorioBorradores repositorioBorradores;
  private final RepositorioPublicacionesProveedor repositorioPublicaciones;
  private final RepositorioMensajesProveedor repositorioMensajes;
  private final AlmacenDeArchivosDeProveedor almacen;

  public DescartarFotoDeBorrador(
      RepositorioBorradores repositorioBorradores,
      RepositorioPublicacionesProveedor repositorioPublicaciones,
      RepositorioMensajesProveedor repositorioMensajes,
      AlmacenDeArchivosDeProveedor almacen) {
    this.repositorioBorradores = Objects.requireNonNull(repositorioBorradores);
    this.repositorioPublicaciones = Objects.requireNonNull(repositorioPublicaciones);
    this.repositorioMensajes = Objects.requireNonNull(repositorioMensajes);
    this.almacen = Objects.requireNonNull(almacen);
  }

  public BorradorProducto ejecutar(UUID borradorId, UUID mensajeId) {
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
    Optional<FotoSubida> subida = borrador.buscarFotoSubida(mensajeId);
    if (subida.isPresent()) {
      almacen.borrar(subida.get().referenciaArchivo());
      borrador.quitarFotoSubida(mensajeId);
    } else {
      if (!publicacion.medios().contains(mensajeId)) {
        throw new FotoNoEsDelBorradorException(mensajeId);
      }
      borrador.descartarFoto(mensajeId);
      // La huella visual sale de la primera foto de la publicación (ResolverBorrador.pHashDe).
      if (publicacion.medios().getFirst().equals(mensajeId)) {
        borrador.olvidarHuellaVisual();
      }
    }
    if (!quedanFotosConArchivo(borrador, publicacion)) {
      borrador.alertarSinFotos();
    }
    repositorioBorradores.actualizar(borrador);
    return borrador;
  }

  /**
   * ¿Queda alguna con la que aprobar? Las subidas siempre tienen archivo; de las del proveedor
   * cuentan las que no se descartaron y no son de una exportación sin adjuntos.
   */
  private boolean quedanFotosConArchivo(
      BorradorProducto borrador, PublicacionProveedor publicacion) {
    if (!borrador.fotosSubidas().isEmpty()) {
      return true;
    }
    Set<UUID> descartadas = borrador.fotosDescartadas();
    return repositorioMensajes.listarDeLote(publicacion.loteId()).stream()
        .anyMatch(
            m ->
                publicacion.medios().contains(m.id())
                    && !descartadas.contains(m.id())
                    && m.referenciaArchivo().isPresent());
  }
}
