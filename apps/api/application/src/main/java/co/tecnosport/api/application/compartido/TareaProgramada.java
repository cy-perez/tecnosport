package co.tecnosport.api.application.compartido;

import java.time.Duration;

/**
 * Un trabajo que corre solo, cada cierto tiempo: conciliar pagos, vencer reservas, drenar la
 * bandeja de salida. Lo corre {@link EjecutarTareasVencidas} cuando le toca (ADR-0104).
 *
 * <p>Reemplaza al {@code @Scheduled}: en los ambientes desplegados la API tiene CPU solo durante
 * una petición, así que una tarea que se programara sola dentro de la aplicación no correría nunca.
 * Una tarea nueva se registra implementando esto, y en ningún otro sitio.
 *
 * <p>{@link #ejecutar} tiene que terminar su trabajo antes de volver: lo que deje corriendo en otro
 * hilo se congela en cuanto el tick responde.
 */
public interface TareaProgramada {

  /**
   * La llave de la tarea en la bitácora. <b>No se cambia</b>: una tarea renombrada nace sin
   * historial y corre en el tick siguiente aunque acabara de correr con el nombre viejo.
   */
  String nombre();

  /** Cuánto se espera desde que terminó la última vez, como el {@code fixedDelay} de antes. */
  Duration intervalo();

  void ejecutar();
}
