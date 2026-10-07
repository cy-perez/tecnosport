package co.tecnosport.api.infrastructure.difusion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.application.difusion.ImagenAPublicar;
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

  /** Cuadrada: dentro del rango que Instagram admite, así que no la descarta ningún filtro. */
  private static final List<ImagenAPublicar> UNA = List.of(new ImagenAPublicar(IMAGEN, 1000, 1000));

  /** Dos cuadradas: el carrusel más corto que existe. */
  private static final List<ImagenAPublicar> DOS =
      List.of(
          new ImagenAPublicar("https://b/uno.jpg", 1000, 1000),
          new ImagenAPublicar("https://b/dos.jpg", 1000, 1000));

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

    ResultadoPublicacion resultado = cliente().publicar(RedSocial.FACEBOOK, UNA, PIE);

    assertTrue(resultado.salioBien(), resultado.motivoDelFallo());
    assertEquals("333_444", resultado.idEnLaRed());
    assertEquals(List.of("/PAGE/photos"), rutasPedidas);
    assertTrue(cuerposRecibidos.get(0).contains("caption=JBL+Grip"), cuerposRecibidos.get(0));
  }

  /** Sin `post_id` se cae al `id` de la foto: peor enlace, pero identificador al fin. */
  @Test
  void siFacebookNoMandaPostIdSirveElIdDeLaFoto() throws IOException {
    levantar(intercambio -> responder(intercambio, 200, "{\"id\":\"111_222\"}"));

    assertEquals("111_222", cliente().publicar(RedSocial.FACEBOOK, UNA, PIE).idEnLaRed());
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

    ResultadoPublicacion resultado = cliente().publicar(RedSocial.INSTAGRAM, UNA, PIE);

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

    ResultadoPublicacion resultado = cliente().publicar(RedSocial.INSTAGRAM, UNA, PIE);

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

    ResultadoPublicacion resultado = cliente().publicar(RedSocial.INSTAGRAM, UNA, PIE);

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

    ResultadoPublicacion resultado = cliente().publicar(RedSocial.FACEBOOK, UNA, PIE);

    assertFalse(resultado.salioBien());
    assertTrue(resultado.motivoDelFallo().contains("unsupported format"), resultado.toString());
  }

  @Test
  void unCuerpoDeErrorQueNoEsElEsperadoNoDejaElMotivoEnBlanco() throws IOException {
    levantar(intercambio -> responder(intercambio, 500, "<html>vaya</html>"));

    ResultadoPublicacion resultado = cliente().publicar(RedSocial.FACEBOOK, UNA, PIE);

    assertFalse(resultado.salioBien());
    assertTrue(resultado.motivoDelFallo().contains("500"), resultado.toString());
  }

  /** El token va en la cabecera: una URL con el token dentro acaba en cualquier registro. */
  @Test
  void elTokenViajaEnLaCabeceraYNoEnLaUrl() throws IOException {
    levantar(intercambio -> responder(intercambio, 200, "{\"id\":\"1\"}"));

    cliente().publicar(RedSocial.FACEBOOK, UNA, PIE);

    assertEquals("Bearer TOKEN", autorizaciones.get(0));
    assertFalse(rutasPedidas.get(0).contains("TOKEN"), rutasPedidas.get(0));
  }

  // --- el carrusel ---

  /**
   * Con varias fotos, Facebook deja de ser un viaje: cada una se sube <b>sin publicar</b> y luego
   * un solo {@code /feed} las reclama por su id. Subirlas publicadas dejaría N posts de una foto en
   * vez de uno con N, que es el defecto que esta prueba atrapa.
   */
  @Test
  void facebookSubeCadaFotoSinPublicarYLuegoArmaElPost() throws IOException {
    levantar(
        intercambio -> {
          String ruta = intercambio.getRequestURI().getPath();
          if (ruta.endsWith("/photos")) {
            responder(intercambio, 200, "{\"id\":\"FOTO" + rutasPedidas.size() + "\"}");
          } else {
            responder(intercambio, 200, "{\"post_id\":\"333_444\"}");
          }
        });

    ResultadoPublicacion resultado = cliente().publicar(RedSocial.FACEBOOK, DOS, PIE);

    assertTrue(resultado.salioBien(), resultado.motivoDelFallo());
    assertEquals("333_444", resultado.idEnLaRed());
    assertEquals(
        List.of("/PAGE/photos", "/PAGE/photos", "/PAGE/feed"),
        rutasPedidas,
        "dos subidas y un post, no dos posts");
    assertTrue(cuerposRecibidos.get(0).contains("published=false"), cuerposRecibidos.get(0));
    assertTrue(cuerposRecibidos.get(2).contains("attached_media"), cuerposRecibidos.get(2));
    assertTrue(cuerposRecibidos.get(2).contains("media_fbid"), cuerposRecibidos.get(2));
  }

  /** Si una subida falla, no se arma el post: saldría con menos fotos y sin decirlo. */
  @Test
  void siUnaSubidaFallaFacebookNoPublicaElPostIncompleto() throws IOException {
    levantar(
        intercambio -> {
          if (rutasPedidas.size() == 1) {
            responder(intercambio, 200, "{\"id\":\"FOTO1\"}");
          } else {
            responder(intercambio, 400, "{\"error\":{\"message\":\"Bad image\"}}");
          }
        });

    ResultadoPublicacion resultado = cliente().publicar(RedSocial.FACEBOOK, DOS, PIE);

    assertFalse(resultado.salioBien());
    assertFalse(rutasPedidas.stream().anyMatch(r -> r.endsWith("/feed")), "no debe publicar");
  }

  /**
   * Instagram: un contenedor hijo por foto con {@code is_carousel_item}, un padre {@code CAROUSEL}
   * con los hijos, y el {@code media_publish} del padre. Se sondea solo el padre.
   */
  @Test
  void instagramCreaUnHijoPorFotoYUnPadreCarrusel() throws IOException {
    AtomicInteger contenedores = new AtomicInteger();
    levantar(
        intercambio -> {
          String ruta = intercambio.getRequestURI().getPath();
          if (ruta.endsWith("/media")) {
            responder(intercambio, 200, "{\"id\":\"C" + contenedores.incrementAndGet() + "\"}");
          } else if (ruta.endsWith("/media_publish")) {
            responder(intercambio, 200, "{\"id\":\"18196134166390376\"}");
          } else {
            responder(intercambio, 200, "{\"status_code\":\"FINISHED\"}");
          }
        });

    ResultadoPublicacion resultado = cliente().publicar(RedSocial.INSTAGRAM, DOS, PIE);

    assertTrue(resultado.salioBien(), resultado.motivoDelFallo());
    assertEquals(3, contenedores.get(), "dos hijos y un padre");
    assertTrue(cuerposRecibidos.get(0).contains("is_carousel_item=true"), cuerposRecibidos.get(0));
    assertTrue(cuerposRecibidos.get(2).contains("media_type=CAROUSEL"), cuerposRecibidos.get(2));
    assertTrue(cuerposRecibidos.get(2).contains("children=C1%2CC2"), cuerposRecibidos.get(2));
    // El pie va en el padre y no en los hijos: un carrusel tiene un solo texto.
    assertFalse(cuerposRecibidos.get(0).contains("caption"), cuerposRecibidos.get(0));
    assertTrue(cuerposRecibidos.get(2).contains("caption"), cuerposRecibidos.get(2));
  }

  // --- qué admite cada red ---

  /**
   * Instagram rechaza lo que se sale de 4:5 a 1,91:1, y en un carrusel eso tumba el post entero por
   * una sola foto. Se descartan antes de crear nada, y el caso de uso guarda en la constancia las
   * que quedaron.
   */
  @Test
  void instagramDescartaLasFotosFueraDeSuRangoDeProporciones() throws IOException {
    levantar(intercambio -> responder(intercambio, 200, "{\"id\":\"1\"}"));
    ImagenAPublicar cuadrada = new ImagenAPublicar("https://b/cuadrada.jpg", 1000, 1000);
    ImagenAPublicar muyAlta = new ImagenAPublicar("https://b/alta.jpg", 600, 1600);
    ImagenAPublicar muyAncha = new ImagenAPublicar("https://b/ancha.jpg", 2400, 1000);

    assertEquals(
        List.of(cuadrada),
        cliente().admitidasPor(RedSocial.INSTAGRAM, List.of(cuadrada, muyAlta, muyAncha)));
  }

  /** Facebook no mira la proporción: recorta o enmarca lo que le llegue. */
  @Test
  void facebookLasAdmiteTodas() throws IOException {
    levantar(intercambio -> responder(intercambio, 200, "{\"id\":\"1\"}"));
    List<ImagenAPublicar> todas =
        List.of(
            new ImagenAPublicar("https://b/alta.jpg", 600, 1600),
            new ImagenAPublicar("https://b/ancha.jpg", 2400, 1000));

    assertEquals(todas, cliente().admitidasPor(RedSocial.FACEBOOK, todas));
  }

  /** Y el carrusel tiene tope: lo que pasa de diez no entra. */
  @Test
  void masDeDiezFotosNoCabenEnElCarrusel() throws IOException {
    levantar(intercambio -> responder(intercambio, 200, "{\"id\":\"1\"}"));
    List<ImagenAPublicar> doce =
        java.util.stream.IntStream.range(0, 12)
            .mapToObj(i -> new ImagenAPublicar("https://b/" + i + ".jpg", 1000, 1000))
            .toList();

    assertEquals(10, cliente().admitidasPor(RedSocial.INSTAGRAM, doce).size());
    assertEquals(10, cliente().admitidasPor(RedSocial.FACEBOOK, doce).size());
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
