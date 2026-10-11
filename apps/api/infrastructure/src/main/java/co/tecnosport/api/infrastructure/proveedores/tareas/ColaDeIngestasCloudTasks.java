package co.tecnosport.api.infrastructure.proveedores.tareas;

import co.tecnosport.api.application.proveedores.ColaDeIngestasNoDisponibleException;
import co.tecnosport.api.application.proveedores.EjecutorDeIngestas;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

/**
 * La cola de ingestas en Cloud Tasks (ADR-0080): cada tramo de un lote es una tarea HTTP que llama
 * al endpoint interno de la API con un token OIDC de la cuenta de servicio de las tareas.
 *
 * <p>Con la API REST v2 ({@code projects.locations.queues.tasks.create}) y el {@code HttpClient}
 * del JDK, como los demás clientes de esta capa, y no con la librería de Cloud Tasks: es una sola
 * llamada. La forma del cuerpo es la de la documentación de «Create HTTP target tasks»: {@code
 * task.httpRequest.{url, httpMethod, oidcToken.{serviceAccountEmail, audience}}} y {@code
 * task.dispatchDeadline} como duración en segundos ({@code "900s"}).
 *
 * <p>La tarea no lleva nombre: Cloud Tasks le pone uno. Un nombre fijo por lote evitaría
 * duplicados, pero Cloud Tasks reserva el nombre de una tarea borrada durante horas, y el mismo
 * lote se encola muchas veces —un tramo tras otro—. Un duplicado no hace daño: el tramo que llega
 * segundo encuentra el lote tomado, o cerrado, y no hace nada.
 */
public final class ColaDeIngestasCloudTasks implements EjecutorDeIngestas {

  private static final Logger log = LoggerFactory.getLogger(ColaDeIngestasCloudTasks.class);

  private final HttpClient httpClient;
  private final JsonMapper json = JsonMapper.builder().build();
  private final URI urlDeLaApi;
  private final String cola;
  private final String urlDeTramos;
  private final String cuentaDeServicio;
  private final String audiencia;
  private final Duration plazoDeLaTarea;
  private final Supplier<String> tokenDeAcceso;

  /**
   * @param urlDeLaApi {@code https://cloudtasks.googleapis.com}; configurable para las pruebas
   * @param cola {@code projects/{p}/locations/{l}/queues/{q}}
   * @param urlDeTramos la URL del endpoint interno sin el lote: {@code .../internal/ingestas}
   * @param cuentaDeServicio la que firma el token con que la tarea llama a la API
   * @param audiencia la audiencia del token; la misma que comprueba el endpoint
   * @param plazoDeLaTarea cuánto espera Cloud Tasks la respuesta de un tramo: de 15 segundos a 30
   *     minutos
   * @param tokenDeAcceso un token OAuth vigente de la cuenta de la API, para llamar a Cloud Tasks
   */
  public ColaDeIngestasCloudTasks(
      URI urlDeLaApi,
      String cola,
      String urlDeTramos,
      String cuentaDeServicio,
      String audiencia,
      Duration plazoDeLaTarea,
      Supplier<String> tokenDeAcceso) {
    this.urlDeLaApi = Objects.requireNonNull(urlDeLaApi);
    this.cola = exigir(cola, "la cola");
    this.urlDeTramos = exigir(urlDeTramos, "la URL de los tramos").replaceAll("/+$", "");
    this.cuentaDeServicio = exigir(cuentaDeServicio, "la cuenta de servicio de las tareas");
    this.audiencia = exigir(audiencia, "la audiencia del token");
    this.plazoDeLaTarea = Objects.requireNonNull(plazoDeLaTarea);
    if (plazoDeLaTarea.compareTo(Duration.ofSeconds(15)) < 0
        || plazoDeLaTarea.compareTo(Duration.ofMinutes(30)) > 0) {
      throw new IllegalArgumentException(
          "Cloud Tasks admite un plazo de tarea HTTP entre 15 segundos y 30 minutos.");
    }
    this.tokenDeAcceso = Objects.requireNonNull(tokenDeAcceso);
    this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
  }

  private static String exigir(String valor, String que) {
    if (valor == null || valor.isBlank()) {
      throw new IllegalArgumentException("Falta " + que + " de Cloud Tasks.");
    }
    return valor.strip();
  }

  @Override
  public void encolar(UUID loteId) {
    Objects.requireNonNull(loteId);
    String cuerpo =
        json.writeValueAsString(
            Map.of(
                "task",
                Map.of(
                    "httpRequest",
                    Map.of(
                        "url",
                        urlDeTramos + "/" + loteId + "/tramo",
                        "httpMethod",
                        "POST",
                        "oidcToken",
                        Map.of("serviceAccountEmail", cuentaDeServicio, "audience", audiencia)),
                    "dispatchDeadline",
                    plazoDeLaTarea.toSeconds() + "s")));
    String token;
    try {
      token = tokenDeAcceso.get();
    } catch (RuntimeException e) {
      throw new ColaDeIngestasNoDisponibleException(e);
    }
    HttpRequest peticion =
        HttpRequest.newBuilder(urlDeLaApi.resolve("/v2/" + cola + "/tasks"))
            .timeout(Duration.ofSeconds(20))
            .header("Authorization", "Bearer " + token)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(cuerpo, StandardCharsets.UTF_8))
            .build();
    HttpResponse<String> respuesta;
    try {
      respuesta = httpClient.send(peticion, HttpResponse.BodyHandlers.ofString());
    } catch (IOException e) {
      throw new ColaDeIngestasNoDisponibleException(e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ColaDeIngestasNoDisponibleException(e);
    }
    if (respuesta.statusCode() / 100 != 2) {
      // El cuerpo de un error de Google dice qué permiso o qué campo falló, y no trae datos de
      // nadie: es lo que hace falta para arreglarlo.
      throw new ColaDeIngestasNoDisponibleException(
          "Cloud Tasks respondió " + respuesta.statusCode() + ": " + respuesta.body());
    }
    log.debug("Tramo del lote {} encolado en {}", loteId, cola);
  }

  @Override
  public boolean sobreviveAUnReinicio() {
    return true;
  }
}
