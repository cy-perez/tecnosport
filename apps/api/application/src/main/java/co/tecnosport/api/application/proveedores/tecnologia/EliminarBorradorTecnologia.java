package co.tecnosport.api.application.proveedores.tecnologia;

import co.tecnosport.api.application.compartido.EnTransaccionPropia;
import co.tecnosport.api.application.proveedores.AlmacenDeArchivosDeProveedor;
import co.tecnosport.api.application.proveedores.BorradorNoEncontradoException;
import co.tecnosport.api.domain.proveedores.BorradorTecnologia;
import co.tecnosport.api.domain.proveedores.EstadoBorrador;
import co.tecnosport.api.domain.proveedores.FotoDeBorradorTecnologia;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
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
 * <p><b>La fila primero, en su propia transacción, y los objetos del bucket después de
 * confirmarla</b> (revisión adversarial del 27 de octubre de 2026). Hasta entonces se borraban los
 * objetos antes que la fila y todo dentro de la transacción del controlador —la del borrado en
 * bloque, una sola para toda la tanda—: un fallo después de borrar las fotos revertía las filas, y
 * quedaban borradores en revisión apuntando a fotos que ya no existían, sin que nadie lo notara
 * hasta abrirlos. El bucket es un tercero que ninguna transacción revierte, y por eso esto usa
 * {@link EnTransaccionPropia}. Al revés, el fallo deja objetos huérfanos en el bucket privado —sin
 * fila que los nombre, nadie los ve—, que es el daño menor y lo que {@code npm run huerfanos}
 * encuentra. Las configuraciones y las fotos de la base se van en cascada.
 */
public final class EliminarBorradorTecnologia {

  static final Set<EstadoBorrador> ELIMINABLES =
      EnumSet.of(EstadoBorrador.EN_REVISION, EstadoBorrador.RECHAZADO);

  private final RepositorioBorradoresTecnologia repositorio;
  private final AlmacenDeArchivosDeProveedor almacen;
  private final EnTransaccionPropia enTransaccionPropia;

  public EliminarBorradorTecnologia(
      RepositorioBorradoresTecnologia repositorio,
      AlmacenDeArchivosDeProveedor almacen,
      EnTransaccionPropia enTransaccionPropia) {
    this.repositorio = Objects.requireNonNull(repositorio);
    this.almacen = Objects.requireNonNull(almacen);
    this.enTransaccionPropia = Objects.requireNonNull(enTransaccionPropia);
  }

  /**
   * Abre su propia transacción: quien llama no la abre. Devuelve cuántos objetos se borraron del
   * bucket, para que quien llame lo registre.
   */
  public int ejecutar(UUID id) {
    List<String> referencias =
        enTransaccionPropia.ejecutar(
            () -> {
              BorradorTecnologia borrador =
                  repositorio
                      .buscarPorIdParaActualizar(id)
                      .orElseThrow(() -> new BorradorNoEncontradoException(id));
              if (!ELIMINABLES.contains(borrador.estado())) {
                throw new BorradorTecnologiaNoEliminableException(borrador.estado());
              }
              List<String> suyas = new ArrayList<>();
              for (FotoDeBorradorTecnologia foto : borrador.fotos()) {
                suyas.addAll(foto.referencias());
              }
              repositorio.eliminar(id);
              return suyas;
            });
    referencias.forEach(almacen::borrar);
    return referencias.size();
  }
}
