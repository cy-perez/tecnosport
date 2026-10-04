package co.tecnosport.api.application.proveedores;

import co.tecnosport.api.domain.proveedores.BorradorProducto;
import co.tecnosport.api.domain.proveedores.EstadoBorrador;
import co.tecnosport.api.domain.proveedores.MensajeProveedor;
import co.tecnosport.api.domain.proveedores.PublicacionProveedor;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Borra un borrador con todo lo que lo sostiene: la publicación, los mensajes del proveedor y sus
 * fotos en el bucket privado. Es lo contrario de {@link RechazarBorrador}, que deja la constancia.
 *
 * <p><b>En revisión o rechazado, y también un aprobado o una renovación cuyo producto ya se
 * borró.</b> Mientras el producto exista, esos dos son la memoria con que la ingesta reconoce un
 * producto que vuelve con otro texto ({@link RepositorioBorradores#huellasVisualesDelProveedor}):
 * borrarlos la deja ciega sin avisar. Sin producto ya no reconocen nada —la huella visual solo
 * cuenta borradores con producto— y lo único que guardan son las fotos del proveedor en el bucket
 * privado, que sin esto quedaban ahí hasta borrar la ingesta entera.
 *
 * <p><b>Lo compartido no se toca.</b> Un mensaje con varios productos da varios borradores sobre la
 * misma publicación; si queda otro, solo se borra la fila de este. Y un mensaje que otra
 * publicación también usa —rehacer la agrupación de un lote arma otras sobre los mismos mensajes—
 * se queda con su foto.
 *
 * <p><b>Borrar los mensajes tiene una consecuencia</b>: son lo que hace inofensivo volver a subir
 * la misma exportación ({@link RepositorioMensajesProveedor#idsExternosExistentes}). Sin ellos, una
 * exportación que vuelva a traer el anuncio lo convierte otra vez en borrador. Es lo que se pidió.
 *
 * <p>Los objetos del bucket se borran antes que las filas, por la razón de {@code
 * EliminarProducto}: al revés, un fallo a mitad deja objetos que ya ninguna fila nombra. Así, un
 * fallo deja filas que apuntan a fotos que ya no están, y reintentar termina el trabajo. El ZIP de
 * la exportación ({@code lote_ingesta.referencia_archivo}) no se toca: es el lote entero.
 */
public final class EliminarBorrador {

  private static final Set<EstadoBorrador> ELIMINABLES =
      EnumSet.of(EstadoBorrador.EN_REVISION, EstadoBorrador.RECHAZADO);

  private final RepositorioBorradores repositorioBorradores;
  private final RepositorioPublicacionesProveedor repositorioPublicaciones;
  private final RepositorioMensajesProveedor repositorioMensajes;
  private final AlmacenDeArchivosDeProveedor almacen;

  public EliminarBorrador(
      RepositorioBorradores repositorioBorradores,
      RepositorioPublicacionesProveedor repositorioPublicaciones,
      RepositorioMensajesProveedor repositorioMensajes,
      AlmacenDeArchivosDeProveedor almacen) {
    this.repositorioBorradores = Objects.requireNonNull(repositorioBorradores);
    this.repositorioPublicaciones = Objects.requireNonNull(repositorioPublicaciones);
    this.repositorioMensajes = Objects.requireNonNull(repositorioMensajes);
    this.almacen = Objects.requireNonNull(almacen);
  }

  /** Devuelve cuántos objetos se borraron del bucket, para que quien llame lo registre. */
  public int ejecutar(UUID borradorId) {
    BorradorProducto borrador =
        repositorioBorradores
            .buscarPorId(borradorId)
            .orElseThrow(() -> new BorradorNoEncontradoException(borradorId));
    if (!ELIMINABLES.contains(borrador.estado()) && borrador.productoId().isPresent()) {
      throw new BorradorNoEliminableException(borrador.estado());
    }

    UUID publicacionId = borrador.publicacionId();
    if (repositorioBorradores.contarDePublicacion(publicacionId) > 1) {
      repositorioBorradores.eliminar(borradorId);
      return 0;
    }

    PublicacionProveedor publicacion =
        repositorioPublicaciones
            .buscarPorId(publicacionId)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "El borrador apunta a una publicación que no existe."));
    Set<UUID> propios = new LinkedHashSet<>();
    propios.add(publicacion.mensajePrincipalId());
    propios.addAll(publicacion.textosAdicionales());
    propios.addAll(publicacion.medios());
    propios.removeAll(repositorioPublicaciones.mensajesUsadosPorOtras(publicacionId, propios));

    List<MensajeProveedor> aBorrar =
        repositorioMensajes.listarDeLote(publicacion.loteId()).stream()
            .filter(m -> propios.contains(m.id()))
            .toList();
    int objetos = 0;
    for (MensajeProveedor mensaje : aBorrar) {
      if (mensaje.referenciaArchivo().isPresent()) {
        almacen.borrar(mensaje.referenciaArchivo().get());
        objetos++;
      }
    }

    repositorioBorradores.eliminar(borradorId);
    repositorioPublicaciones.eliminar(publicacionId);
    repositorioMensajes.eliminarTodos(aBorrar.stream().map(MensajeProveedor::id).toList());
    return objetos;
  }
}
