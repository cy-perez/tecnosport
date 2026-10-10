package co.tecnosport.api.application.proveedores;

import co.tecnosport.api.domain.proveedores.EstadoBorrador;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Borra de un golpe los borradores que nadie aprobó —en revisión o rechazados—, cada uno con lo que
 * {@link EliminarBorrador} se lleva: su publicación, sus mensajes y sus fotos del bucket privado.
 * Es la limpieza de la bandeja, no una decisión sobre cada anuncio.
 *
 * <p><b>Solo esos dos estados</b>, y no los que {@link EliminarBorrador} admite además —un aprobado
 * o una renovación cuyo producto ya se borró—: lo que se pidió es borrar lo que no se aprobó, y un
 * aprobado huérfano es constancia de algo que sí se aprobó. Uno por uno se sigue pudiendo.
 *
 * <p><b>Uno por uno, con {@link EliminarBorrador}</b>, y no con un borrado masivo propio: lo
 * compartido —una publicación con varios borradores, un mensaje que otra publicación también usa—
 * ya lo resuelve él, y una segunda copia de esa regla terminaría distinta de la primera. Dos
 * borradores de la misma publicación en la misma tanda salen bien por eso mismo: el primero solo se
 * lleva su fila y el segundo, que ya es el último, se lleva la publicación.
 *
 * <p><b>Por tandas.</b> Cada llamada borra como mucho {@code tamanoTanda} y dice cuántos quedan; el
 * panel repite. Una bandeja de miles en una sola petición rozaría el límite de tiempo del servidor
 * con una transacción abierta todo ese rato. La tanda corre en la transacción del controlador, como
 * {@link EliminarLoteDeIngesta}: si algo falla, las filas vuelven y los archivos ya no, y repetir
 * termina el trabajo porque borrar un objeto que no está no falla.
 *
 * <p>Un borrador que alguien aprueba o borra entre la lista y su turno se salta, sin tumbar la
 * tanda: ya no es de los que se querían borrar.
 */
public final class EliminarBorradoresSinAprobar {

  static final Set<EstadoBorrador> SIN_APROBAR =
      EnumSet.of(EstadoBorrador.EN_REVISION, EstadoBorrador.RECHAZADO);

  private final RepositorioBorradores repositorioBorradores;
  private final EliminarBorrador eliminarBorrador;

  public EliminarBorradoresSinAprobar(
      RepositorioBorradores repositorioBorradores, EliminarBorrador eliminarBorrador) {
    this.repositorioBorradores = Objects.requireNonNull(repositorioBorradores);
    this.eliminarBorrador = Objects.requireNonNull(eliminarBorrador);
  }

  /** Cuántos borraría, para que el panel lo diga antes de confirmar. */
  public long contar() {
    return repositorioBorradores.contarEnEstados(SIN_APROBAR);
  }

  public BorradoresEliminados ejecutar(int tamanoTanda) {
    if (tamanoTanda < 1) {
      throw new IllegalArgumentException("La tanda tiene que tener al menos un borrador.");
    }
    int eliminados = 0;
    int archivos = 0;
    for (UUID id : repositorioBorradores.idsEnEstados(SIN_APROBAR, tamanoTanda)) {
      try {
        archivos += eliminarBorrador.ejecutar(id);
        eliminados++;
      } catch (BorradorNoEncontradoException | BorradorNoEliminableException e) {
        // Lo aprobaron o lo borraron mientras tanto: ya no es de los que se querían borrar.
      }
    }
    return new BorradoresEliminados(eliminados, archivos, contar());
  }
}
