package co.tecnosport.api.bootstrap.proveedores;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * La cola de ingestas en Cloud Tasks (ADR-0080). Sin {@code cola}, la ingesta corre en un hilo con
 * su cola en memoria, que es lo de local y las pruebas; con ella, las demás son obligatorias y el
 * arranque falla si falta alguna, antes de que un lote se quede sin tarea.
 *
 * @param cola {@code projects/{p}/locations/{l}/queues/{q}}; vacía para la cola en memoria
 * @param urlTramos la URL pública del endpoint interno sin el lote: {@code
 *     .../api/v1/internal/ingestas}
 * @param cuentaDeServicio la cuenta con que Cloud Tasks firma el token de cada tarea
 * @param audiencia la audiencia de ese token; la URL del servicio
 * @param urlApi la de Cloud Tasks; configurable solo para probar contra otra cosa
 * @param urlLlaves las llaves públicas con que Google firma los tokens
 */
@ConfigurationProperties(prefix = "tecnosport.proveedores.cola-de-tareas")
public record PropiedadesColaDeIngestas(
    String cola,
    String urlTramos,
    String cuentaDeServicio,
    String audiencia,
    String urlApi,
    String urlLlaves) {

  public PropiedadesColaDeIngestas {
    if (enCloudTasks(cola)) {
      exigir("url-tramos", urlTramos);
      exigir("cuenta-de-servicio", cuentaDeServicio);
      exigir("audiencia", audiencia);
      exigir("url-api", urlApi);
      exigir("url-llaves", urlLlaves);
      if (!cola.strip().matches("projects/[^/]+/locations/[^/]+/queues/[^/]+")) {
        throw new IllegalStateException(
            "tecnosport.proveedores.cola-de-tareas.cola tiene que tener la forma"
                + " projects/{p}/locations/{l}/queues/{q}.");
      }
    }
  }

  private static boolean enCloudTasks(String cola) {
    return cola != null && !cola.isBlank();
  }

  private static void exigir(String nombre, String valor) {
    if (valor == null || valor.isBlank()) {
      throw new IllegalStateException(
          "Con la cola de Cloud Tasks configurada, falta tecnosport.proveedores.cola-de-tareas."
              + nombre
              + ".");
    }
  }

  public boolean enCloudTasks() {
    return enCloudTasks(cola);
  }
}
