package co.tecnosport.api.application.proveedores.tecnologia;

import co.tecnosport.api.application.proveedores.BorradorNoEncontradoException;
import co.tecnosport.api.application.proveedores.BorradoresEliminados;
import java.util.Objects;
import java.util.UUID;

/**
 * Borra de un golpe los borradores de tecnología que nadie aprobó —en revisión o rechazados—, cada
 * uno con {@link EliminarBorradorTecnologia}, que se lleva sus fotos del bucket. Es la limpieza de
 * la bandeja antes de rehacer una tanda (24/10/2026); los aprobados se quedan.
 *
 * <p><b>Por tandas</b>, con la forma de {@code EliminarBorradoresSinAprobar}: cada llamada borra
 * como mucho {@code tamanoTanda} y dice cuántos quedan, y el panel repite. Un borrador lleva unas
 * quince piezas en el bucket —cada foto con sus anchos y su vista previa—, y una bandeja entera en
 * una sola petición mantendría la transacción abierta cientos de borrados.
 *
 * <p>Un borrador que alguien aprueba o borra entre la lista y su turno se salta, sin tumbar la
 * tanda: ya no es de los que se querían borrar.
 */
public final class EliminarBorradoresTecnologiaSinAprobar {

  private final RepositorioBorradoresTecnologia repositorio;
  private final EliminarBorradorTecnologia eliminarBorrador;

  public EliminarBorradoresTecnologiaSinAprobar(
      RepositorioBorradoresTecnologia repositorio, EliminarBorradorTecnologia eliminarBorrador) {
    this.repositorio = Objects.requireNonNull(repositorio);
    this.eliminarBorrador = Objects.requireNonNull(eliminarBorrador);
  }

  /** Cuántos borraría, para que el panel lo diga antes de confirmar. */
  public long contar() {
    return repositorio.contarEnEstados(EliminarBorradorTecnologia.ELIMINABLES);
  }

  public BorradoresEliminados ejecutar(int tamanoTanda) {
    if (tamanoTanda < 1) {
      throw new IllegalArgumentException("La tanda tiene que tener al menos un borrador.");
    }
    int eliminados = 0;
    int archivos = 0;
    for (UUID id : repositorio.idsEnEstados(EliminarBorradorTecnologia.ELIMINABLES, tamanoTanda)) {
      try {
        archivos += eliminarBorrador.ejecutar(id);
        eliminados++;
      } catch (BorradorNoEncontradoException | BorradorTecnologiaNoEliminableException e) {
        // Lo aprobaron o lo borraron mientras tanto: ya no es de los que se querían borrar.
      }
    }
    return new BorradoresEliminados(eliminados, archivos, contar());
  }
}
