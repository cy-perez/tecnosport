package co.tecnosport.api.application.compartido;

import java.time.Duration;
import java.util.Objects;

/**
 * Un trabajo que corre solo, cada cierto tiempo: conciliar pagos, vencer reservas, drenar la
 * bandeja de salida. Lo corre {@link EjecutarTareasVencidas} cuando le toca (ADR-0104).
 *
 * <p>Reemplaza al {@code @Scheduled}: en los ambientes desplegados la API tiene CPU solo durante
 * una petición, así que una tarea que se programara sola dentro de la aplicación no correría nunca.
 * Las del sistema se registran con {@link #de} en {@code CatalogoDeTareasProgramadas}, de {@code
 * bootstrap}, que es quien cablea —y no implementa— lo de {@code application}.
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

  /** Una tarea con nombre e intervalo cuyo trabajo es {@code trabajo}. */
  static TareaProgramada de(String nombre, Duration intervalo, Runnable trabajo) {
    return new DeUnTrabajo(nombre, intervalo, trabajo);
  }

  /** Lo que devuelve {@link #de}. */
  record DeUnTrabajo(String nombre, Duration intervalo, Runnable trabajo)
      implements TareaProgramada {

    public DeUnTrabajo {
      Objects.requireNonNull(nombre);
      Objects.requireNonNull(trabajo);
      if (nombre.isBlank()) {
        throw new IllegalArgumentException("Una tarea programada necesita nombre.");
      }
      if (intervalo == null || intervalo.isNegative() || intervalo.isZero()) {
        throw new IllegalArgumentException(
            "El intervalo de la tarea " + nombre + " debe ser mayor que cero.");
      }
    }

    @Override
    public void ejecutar() {
      trabajo.run();
    }
  }
}
