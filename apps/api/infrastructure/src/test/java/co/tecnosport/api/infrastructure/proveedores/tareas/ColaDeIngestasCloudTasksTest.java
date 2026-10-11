package co.tecnosport.api.infrastructure.proveedores.tareas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.application.proveedores.ColaDeIngestasNoDisponibleException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Lo que se le manda a Cloud Tasks por cada tramo, contra un servidor local que hace de Google. */
class ColaDeIngestasCloudTasksTest {

  private static final String COLA =
      "projects/tecnosport-dev/locations/us-east1/queues/ingestas-proveedores";
  private static final String TRAMOS = "https://api.example.run.app/api/v1/internal/ingestas/";
  private static final String CUENTA = "ingesta-tareas@tecnosport-dev.iam.gserviceaccount.com";
  private static final String AUDIENCIA = "https://api.example.run.app";
  private static final UUID LOTE = UUID.fromString("01a1299c-2751-7bd2-8ddf-18c1b99312e5");

  private final List<String> rutas = new ArrayList<>();
  private final List<String> autorizaciones = new ArrayList<>();
  private final List<String> cuerpos = new ArrayList<>();
  private HttpServer servidor;

  @AfterEach
  void apagar() {
    if (servidor != null) {
      servidor.stop(0);
    }
  }

  private void servidorQueResponde(int estado, String cuerpo) throws IOException {
    servidor = HttpServer.create(new InetSocketAddress(0), 0);
    servidor.createContext(
        "/",
        intercambio -> {
          rutas.add(intercambio.getRequestMethod() + " " + intercambio.getRequestURI().getPath());
          autorizaciones.add(intercambio.getRequestHeaders().getFirst("Authorization"));
          cuerpos.add(
              new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          responder(intercambio, estado, cuerpo);
        });
    servidor.start();
  }

  private static void responder(HttpExchange intercambio, int estado, String cuerpo)
      throws IOException {
    byte[] bytes = cuerpo.getBytes(StandardCharsets.UTF_8);
    intercambio.sendResponseHeaders(estado, bytes.length);
    try (OutputStream salida = intercambio.getResponseBody()) {
      salida.write(bytes);
    }
  }

  private ColaDeIngestasCloudTasks cola() {
    return new ColaDeIngestasCloudTasks(
        URI.create("http://localhost:" + servidor.getAddress().getPort()),
        COLA,
        TRAMOS,
        CUENTA,
        AUDIENCIA,
        Duration.ofMinutes(15),
        () -> "TOKEN");
  }

  @Test
  void creaUnaTareaHttpQueLlamaAlTramoDelLoteConElTokenDeLaCuentaDeLasTareas() throws IOException {
    servidorQueResponde(200, "{\"name\":\"" + COLA + "/tasks/123\"}");

    cola().encolar(LOTE);

    assertEquals(List.of("POST /v2/" + COLA + "/tasks"), rutas);
    assertEquals("Bearer TOKEN", autorizaciones.get(0));
    JsonNode tarea = JsonMapper.builder().build().readTree(cuerpos.get(0)).path("task");
    JsonNode http = tarea.path("httpRequest");
    assertEquals(
        "https://api.example.run.app/api/v1/internal/ingestas/" + LOTE + "/tramo",
        http.path("url").asString(),
        "sin la barra doble de la URL configurada con barra final");
    assertEquals("POST", http.path("httpMethod").asString());
    assertEquals(CUENTA, http.path("oidcToken").path("serviceAccountEmail").asString());
    assertEquals(AUDIENCIA, http.path("oidcToken").path("audience").asString());
    assertEquals("900s", tarea.path("dispatchDeadline").asString());
  }

  /** Un error de Google sale como cola no disponible, con lo que Google dijo para el registro. */
  @Test
  void unErrorDeCloudTasksEsColaNoDisponible() throws IOException {
    servidorQueResponde(403, "{\"error\":{\"message\":\"Permission denied on actAs\"}}");

    ColaDeIngestasNoDisponibleException error =
        assertThrows(ColaDeIngestasNoDisponibleException.class, () -> cola().encolar(LOTE));

    assertTrue(error.getCause().getMessage().contains("403"), error.getCause().getMessage());
    assertTrue(error.getCause().getMessage().contains("actAs"));
  }

  @Test
  void sinTokenDeAccesoTambienEsColaNoDisponible() throws IOException {
    servidorQueResponde(200, "{}");
    ColaDeIngestasCloudTasks sinCredenciales =
        new ColaDeIngestasCloudTasks(
            URI.create("http://localhost:" + servidor.getAddress().getPort()),
            COLA,
            TRAMOS,
            CUENTA,
            AUDIENCIA,
            Duration.ofMinutes(15),
            () -> {
              throw new IllegalStateException("sin credenciales");
            });

    assertThrows(ColaDeIngestasNoDisponibleException.class, () -> sinCredenciales.encolar(LOTE));
    assertEquals(List.of(), rutas, "sin token no se llama a nadie");
  }

  /**
   * Cloud Tasks admite entre 15 segundos y 30 minutos; fuera de eso, al arrancar y no al encolar.
   */
  @Test
  void unPlazoQueCloudTasksNoAdmiteNoArranca() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ColaDeIngestasCloudTasks(
                URI.create("http://localhost"),
                COLA,
                TRAMOS,
                CUENTA,
                AUDIENCIA,
                Duration.ofMinutes(31),
                () -> "TOKEN"));
  }

  @Test
  void loEncoladoSobreviveAUnReinicio() throws IOException {
    servidorQueResponde(200, "{}");

    assertTrue(cola().sobreviveAUnReinicio());
  }
}
