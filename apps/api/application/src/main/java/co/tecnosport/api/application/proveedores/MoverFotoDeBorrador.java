package co.tecnosport.api.application.proveedores;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import co.tecnosport.api.domain.proveedores.BorradorProducto;
import co.tecnosport.api.domain.proveedores.EstadoBorrador;
import co.tecnosport.api.domain.proveedores.PublicacionProveedor;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Pasa una foto del proveedor de un borrador a otro (10 de octubre de 2026): la del jean que quedó
 * en el bodi del conjunto, la del diseño que el lector puso en el borrador vecino. En el origen se
 * descarta como lo haría {@link DescartarFotoDeBorrador} —con la huella visual olvidada y la alerta
 * {@code SIN_FOTOS} si se queda sin ninguna—; en el destino se recupera si es de su misma
 * publicación, o se suma como foto de otra publicación si no.
 *
 * <p>Los dos borradores tienen que estar en revisión y ser del mismo proveedor. Una foto subida
 * desde el panel no se mueve: es un archivo de su borrador, y se sube otra vez en el que toque. La
 * transacción la abre quien llama: escribe dos borradores, y los dos o ninguno.
 */
public final class MoverFotoDeBorrador {

  private final RepositorioBorradores repositorioBorradores;
  private final RepositorioPublicacionesProveedor repositorioPublicaciones;
  private final DescartarFotoDeBorrador descartar;

  public MoverFotoDeBorrador(
      RepositorioBorradores repositorioBorradores,
      RepositorioPublicacionesProveedor repositorioPublicaciones,
      DescartarFotoDeBorrador descartar) {
    this.repositorioBorradores = Objects.requireNonNull(repositorioBorradores);
    this.repositorioPublicaciones = Objects.requireNonNull(repositorioPublicaciones);
    this.descartar = Objects.requireNonNull(descartar);
  }

  /**
   * @return el borrador de destino, ya con la foto
   */
  public BorradorProducto ejecutar(UUID origenId, UUID mensajeId, UUID destinoId) {
    Objects.requireNonNull(mensajeId, "La foto no puede ser nula.");
    if (Objects.equals(origenId, destinoId)) {
      throw new ExcepcionDeDominio("La foto ya está en ese borrador.");
    }
    BorradorProducto origen =
        repositorioBorradores
            .buscarPorId(origenId)
            .orElseThrow(() -> new BorradorNoEncontradoException(origenId));
    BorradorProducto destino =
        repositorioBorradores
            .buscarPorIdParaActualizar(destinoId)
            .orElseThrow(() -> new BorradorNoEncontradoException(destinoId));
    if (destino.estado() != EstadoBorrador.EN_REVISION) {
      throw new BorradorNoEditableException(destino.estado());
    }
    if (!origen.proveedorId().equals(destino.proveedorId())) {
      throw new ExcepcionDeDominio("Una foto solo se mueve entre borradores del mismo proveedor.");
    }
    if (origen.buscarFotoSubida(mensajeId).isPresent()) {
      throw new ExcepcionDeDominio(
          "Una foto subida desde el panel no se mueve: súbela en el otro borrador.");
    }
    if (origen.fotosDescartadas().contains(mensajeId)) {
      throw new FotoNoEsDelBorradorException(mensajeId);
    }

    // El descarte valida que la foto sea del origen y lo guarda con su bloqueo.
    descartar.ejecutar(origenId, mensajeId);

    List<UUID> deSuPublicacion =
        repositorioPublicaciones
            .buscarPorId(destino.publicacionId())
            .map(PublicacionProveedor::medios)
            .orElse(List.of());
    if (deSuPublicacion.contains(mensajeId)) {
      if (destino.fotosDescartadas().contains(mensajeId)) {
        destino.recuperarFoto(mensajeId);
      }
    } else {
      destino.agregarFotos(List.of(mensajeId), deSuPublicacion);
    }
    repositorioBorradores.actualizar(destino);
    return destino;
  }
}
