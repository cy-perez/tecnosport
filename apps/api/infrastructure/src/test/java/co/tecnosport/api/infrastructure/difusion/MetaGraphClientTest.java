package co.tecnosport.api.infrastructure.difusion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.application.difusion.ResultadoPublicacion;
import co.tecnosport.api.domain.difusion.RedSocial;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** El protocolo contra un servidor local, sin depender de Meta. */
class MetaGraphClientTest {

  private static final String IMAGEN = "https://storage.googleapis.com/b/principal.jpg";
  private static final String PIE = "JBL Grip — $299.900";

  private HttpServer servidor;
  private final List<String> rutasPedidas = new ArrayList<>();
  private final List<String> cuerposRecibidos = new ArrayList<>();
  private final List<String> autorizaciones = new ArrayList<>();

  @AfterEach
  void apagar() {
    if (servidor != null) {
      servidor.stop(0);
    }
  }

  @Test
  void facebookPublicaEnUnSoloViajeYDevuelveElIdDelPost() throws IOException {
    levantar(
        intercambio -> {
          responder(intercambio, 200, "{\"id\":\"111_222\",\"post_id\":\"333_444\"}");
        });

    ResultadoPublicacion resultado = cliente().publicar(RedSocial.FACEBOOK, IMAGEN, PIE);

    assertTrue(resultado.salioBien(), resultado.motivoDelFallo());
    assertEquals("333_444", resultado.idEnLaRed());
    assertEquals(List.of("/PAGE/photos"), rutasPedidas);
    assertTrue(cuerposRecibidos.get(0).contains("caption=JBL+Grip"), cuerposRecibidos.get(0));
  }

  /** Sin `post_id` se cae al `id` de la foto: peor enlace, pero identificador al fin. */
  @Test
  void siFacebookNoMandaPostIdSirveElIdDeLaFoto() throws IOException {
    levantar(intercambio -> responder(intercambio, 200, "{\"id\":\"111_222\"}"));

    assertEquals("111_222", cliente().publicar(RedSocial.FACEBOOK, IMAGEN, PIE).idEnLaRed());
  }

  /** Los tres viajes de Instagram: crear el contenedor, sondearlo y publicarlo. */
  @Test
  void instagramCreaSondeaYPublica() throws IOException {
    AtomicInteger sondeos = new AtomicInteger();
    levantar(
        intercambio -> {
          String ruta = intercambio.getRequestURI().getPath();
          if (ruta.endsWith("/media")) {
            responder(intercambio, 200, "{\"id\":\"CONTENEDOR\"}");
          } else if (ruta.endsWith("/media_publish")) {
            responder(intercambio, 200, "{\"id\":\"18196134166390376\"}");
          } else {
            // El primero sale IN_PROGRESS a propósito: si el cliente no sondeara, publicaría
            // un contenedor a medias y Meta devolvería un error que parece de permisos.
            boolean listo = sondeos.incrementAndGet() > 1;
            responder(
                intercambio,
                200,
                "{\"status_code\":\"" + (listo ? "FINISHED" : "IN_PROGRESS") + "\"}");
          }
        });

    ResultadoPublicacion resultado = cliente().publicar(RedSocial.INSTAGRAM, IMAGEN, PIE);

    assertTrue(resultado.salioBien(), resultado.motivoDelFallo());
    assertEquals("18196134166390376", resultado.idEnLaRed());
    assertEquals(2, sondeos.get());
    assertEquals("/IG/media", rutasPedidas.get(0));
    assertEquals("/IG/media_publish", rutasPedidas.get(rutasPedidas.size() - 1));
  }

  @Test
  void unContenedorEnErrorNoSePublicaYElMotivoLlegaEntero() throws IOException {
    levantar(
        intercambio -> {
          String ruta = intercambio.getRequestURI().getPath();
          if (ruta.endsWith("/media")) {
            responder(intercambio, 200, "{\"id\":\"CONTENEDOR\"}");
          } else {
            responder(
                intercambio,
                200,
                "{\"status_code\":\"ERROR\",\"status\":\"Media download failed\"}");
          }
        });

    ResultadoPublicacion resultado = cliente().publicar(RedSocial.INSTAGRAM, IMAGEN, PIE);

    assertFalse(resultado.salioBien());
    assertTrue(resultado.motivoDelFallo().contains("Media download failed"), resultado.toString());
    assertFalse(
        rutasPedidas.stream().anyMatch(r -> r.endsWith("media_publish")), "no debe publicar");
  }

  /** Agotar el sondeo no es "no salió": es "no nos consta", y el mensaje tiene que decirlo. */
  @Test
  void siElSondeoSeAgotaElMensajePideRevisarLaCuenta() throws IOException {
    levantar(
        intercambio -> {
          String ruta = intercambio.getRequestURI().getPath();
          if (ruta.endsWith("/media")) {
            responder(intercambio, 200, "{\"id\":\"CONTENEDOR\"}");
          } else {
            responder(intercambio, 200, "{\"status_code\":\"IN_PROGRESS\"}");
          }
        });

    ResultadoPublicacion resultado = cliente().publicar(RedSocial.INSTAGRAM, IMAGEN, PIE);

    assertFalse(resultado.salioBien());
    assertTrue(resultado.motivoDelFallo().contains("Revisa la cuenta"), resultado.toString());
  }

  /** Un rechazo es una respuesta, no una excepción: el puerto lo promete y aquí se cumple. */
  @Test
  void unErrorDeMetaVuelveComoResultadoConSuMensaje() throws IOException {
    levantar(
        intercambio ->
            responder(
                intercambio,
                400,
                "{\"error\":{\"message\":\"The image is in an unsupported format\"}}"));

    ResultadoPublicacion resultado = cliente().publicar(RedSocial.FACEBOOK, IMAGEN, PIE);

    assertFalse(resultado.salioBien());
    assertTrue(resultado.motivoDelFallo().contains("unsupported format"), resultado.toString());
  }

  @Test
  void unCuerpoDeErrorQueNoEsElEsperadoNoDejaElMotivoEnBlanco() throws IOException {
    levantar(intercambio -> responder(intercambio, 500, "<html>vaya</html>"));

    ResultadoPublicacion resultado = cliente().publicar(RedSocial.FACEBOOK, IMAGEN, PIE);

    assertFalse(resultado.salioBien());
    assertTrue(resultado.motivoDelFallo().contains("500"), resultado.toString());
  }

  /** El token va en la cabecera: una URL con el token dentro acaba en cualquier registro. */
  @Test
  void elTokenViajaEnLaCabeceraYNoEnLaUrl() throws IOException {
    levantar(intercambio -> responder(intercambio, 200, "{\"id\":\"1\"}"));

    cliente().publicar(RedSocial.FACEBOOK, IMAGEN, PIE);

    assertEquals("Bearer TOKEN", autorizaciones.get(0));
    assertFalse(rutasPedidas.get(0).contains("TOKEN"), rutasPedidas.get(0));
  }

  // --- armado ---

  private MetaGraphClient cliente() {
    return new MetaGraphClient(
        URI.create("http://localhost:" + servidor.getAddress().getPort()),
        "PAGE",
        "IG",
        "TOKEN",
        Duration.ofSeconds(5),
        2,
        Duration.ofMillis(5));
  }

  private void levantar(Manejador manejador) throws IOException {
    servidor = HttpServer.create(new InetSocketAddress(0), 0);
    servidor.createContext(
        "/",
        intercambio -> {
          rutasPedidas.add(intercambio.getRequestURI().getPath());
          autorizaciones.add(intercambio.getRequestHeaders().getFirst("Authorization"));
          cuerposRecibidos.add(
              new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          manejador.atender(intercambio);
        });
    servidor.start();
  }

  private static void responder(HttpExchange intercambio, int codigo, String cuerpo)
      throws IOException {
    byte[] bytes = cuerpo.getBytes(StandardCharsets.UTF_8);
    intercambio.getResponseHeaders().add("Content-Type", "application/json");
    intercambio.sendResponseHeaders(codigo, bytes.length);
    intercambio.getResponseBody().write(bytes);
    intercambio.close();
  }

  @FunctionalInterface
  private interface Manejador {
    void atender(HttpExchange intercambio) throws IOException;
  }
}
