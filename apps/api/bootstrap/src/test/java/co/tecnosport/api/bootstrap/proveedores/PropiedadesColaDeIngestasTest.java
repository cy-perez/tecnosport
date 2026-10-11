package co.tecnosport.api.bootstrap.proveedores;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Con la cola configurada, lo que falte se dice al arrancar y no cuando un lote se queda sin tarea.
 */
class PropiedadesColaDeIngestasTest {

  private static final String COLA =
      "projects/tecnosport-dev/locations/us-east1/queues/ingestas-proveedores";
  private static final String GOOGLE = "https://cloudtasks.googleapis.com";
  private static final String LLAVES = "https://www.googleapis.com/oauth2/v3/certs";

  @Test
  void sinColaEsLaColaEnMemoriaYNoPideNadaMas() {
    assertFalse(new PropiedadesColaDeIngestas("", null, null, null, GOOGLE, LLAVES).enCloudTasks());
    assertFalse(
        new PropiedadesColaDeIngestas(null, null, null, null, GOOGLE, LLAVES).enCloudTasks());
  }

  @Test
  void conColaYTodoLoDemasEsCloudTasks() {
    assertTrue(
        new PropiedadesColaDeIngestas(
                COLA,
                "https://api.run.app/api/v1/internal/ingestas",
                "tareas@p.iam.gserviceaccount.com",
                "https://api.run.app",
                GOOGLE,
                LLAVES)
            .enCloudTasks());
  }

  @Test
  void conColaYSinCuentaNoArranca() {
    assertThrows(
        IllegalStateException.class,
        () ->
            new PropiedadesColaDeIngestas(
                COLA,
                "https://api.run.app/api/v1/internal/ingestas",
                " ",
                "https://api.run.app",
                GOOGLE,
                LLAVES));
  }

  @Test
  void unaColaQueNoEsUnNombreCompletoNoArranca() {
    assertThrows(
        IllegalStateException.class,
        () ->
            new PropiedadesColaDeIngestas(
                "ingestas-proveedores",
                "https://api.run.app/api/v1/internal/ingestas",
                "tareas@p.iam.gserviceaccount.com",
                "https://api.run.app",
                GOOGLE,
                LLAVES));
  }
}
