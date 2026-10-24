package co.tecnosport.api.application.proveedores.tecnologia;

import co.tecnosport.api.application.proveedores.AlmacenDeArchivosDeProveedor;
import co.tecnosport.api.application.proveedores.BorradorNoEncontradoException;
import co.tecnosport.api.domain.proveedores.BorradorTecnologia;
import co.tecnosport.api.domain.proveedores.EstadoBorrador;
import co.tecnosport.api.domain.proveedores.FotoDeBorradorTecnologia;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Borra un borrador de tecnología con sus fotos del bucket privado (24/10/2026). Es lo contrario de
 * {@link RechazarBorradorTecnologia}, que deja la constancia: un rechazado no se vuelve a proponer,
 * y uno borrado sí, con la siguiente importación de la lista. Por eso borrar es la forma de rehacer
 * una tanda desde cero, y rechazar no.
 *
 * <p><b>En revisión o rechazado; nunca aprobado</b> ({@link
 * BorradorTecnologiaNoEliminableException}). Borrar un rechazado deshace la decisión: el modelo
 * vuelve a proponerse.
 *
 * <p>Los objetos del bucket se borran antes que la fila, como en {@code EliminarBorrador}: un fallo
 * a mitad deja una fila que apunta a fotos que ya no están, y repetir termina el trabajo, porque
 * borrar un objeto que no está no falla. Las configuraciones y las fotos de la base se van en
 * cascada.
 */
public final class EliminarBorradorTecnologia {

  static final Set<EstadoBorrador> ELIMINABLES =
      EnumSet.of(EstadoBorrador.EN_REVISION, EstadoBorrador.RECHAZADO);

  private final RepositorioBorradoresTecnologia repositorio;
  private final AlmacenDeArchivosDeProveedor almacen;

  public EliminarBorradorTecnologia(
      RepositorioBorradoresTecnologia repositorio, AlmacenDeArchivosDeProveedor almacen) {
    this.repositorio = Objects.requireNonNull(repositorio);
    this.almacen = Objects.requireNonNull(almacen);
  }

  /** Devuelve cuántos objetos se borraron del bucket, para que quien llame lo registre. */
  public int ejecutar(UUID id) {
    BorradorTecnologia borrador =
        repositorio
            .buscarPorIdParaActualizar(id)
            .orElseThrow(() -> new BorradorNoEncontradoException(id));
    if (!ELIMINABLES.contains(borrador.estado())) {
      throw new BorradorTecnologiaNoEliminableException(borrador.estado());
    }
    int objetos = 0;
    for (FotoDeBorradorTecnologia foto : borrador.fotos()) {
      for (String referencia : foto.referencias()) {
        almacen.borrar(referencia);
        objetos++;
      }
    }
    repositorio.eliminar(id);
    return objetos;
  }
}
