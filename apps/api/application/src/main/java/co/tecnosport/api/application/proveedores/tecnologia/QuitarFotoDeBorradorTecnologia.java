package co.tecnosport.api.application.proveedores.tecnologia;

import co.tecnosport.api.application.proveedores.AlmacenDeArchivosDeProveedor;
import co.tecnosport.api.application.proveedores.BorradorNoEditableException;
import co.tecnosport.api.application.proveedores.BorradorNoEncontradoException;
import co.tecnosport.api.domain.proveedores.BorradorTecnologia;
import co.tecnosport.api.domain.proveedores.EstadoBorrador;
import co.tecnosport.api.domain.proveedores.FotoDeBorradorTecnologia;
import java.util.Objects;
import java.util.UUID;

/**
 * Quita una foto del borrador y borra sus archivos del bucket: no son de nadie más, porque cada
 * clave nace bajo el prefijo de este borrador.
 *
 * <p>Se borra después de guardar, como la principal de un producto. No después del commit —la
 * transacción la abre quien llama—: si el commit falla, el borrador queda apuntando a archivos que
 * ya no están, y aprobarlo falla con {@code ObjetoDeImagenNoEncontradoException} antes de publicar
 * nada; se arregla quitando esa foto otra vez.
 */
public final class QuitarFotoDeBorradorTecnologia {

  private final RepositorioBorradoresTecnologia repositorio;
  private final AlmacenDeArchivosDeProveedor almacen;

  public QuitarFotoDeBorradorTecnologia(
      RepositorioBorradoresTecnologia repositorio, AlmacenDeArchivosDeProveedor almacen) {
    this.repositorio = Objects.requireNonNull(repositorio);
    this.almacen = Objects.requireNonNull(almacen);
  }

  public void ejecutar(UUID borradorId, UUID fotoId) {
    BorradorTecnologia borrador =
        repositorio
            .buscarPorIdParaActualizar(borradorId)
            .orElseThrow(() -> new BorradorNoEncontradoException(borradorId));
    if (borrador.estado() != EstadoBorrador.EN_REVISION) {
      throw new BorradorNoEditableException(borrador.estado());
    }
    FotoDeBorradorTecnologia quitada = borrador.quitarFoto(fotoId);
    repositorio.actualizar(borrador);
    quitada.referencias().forEach(almacen::borrar);
  }
}
