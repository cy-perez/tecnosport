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

  /** El que la Graph API devuelve al preguntarle a la página por su `access_token`. */
  private static final String TOKEN_DE_PAGINA = "TOKEN-DE-LA-PAGINA";

  private HttpServer servidor;
  private final List<String> rutasPedidas = new ArrayList<>();
  private final List<String> cuerposRecibidos = new ArrayList<>();
  private final List<String> autorizaciones = new ArrayList<>();
  private int vecesQueSePidioElTokenDePagina;

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

  /**
   * <b>Escribir en el muro va con el token de la página, no con el del usuario del sistema.</b>
   *
   * <p>Medido contra la cuenta real el 7 de octubre de 2026: al subir la primera foto sin publicar,
   * Meta contestó {@code (#200) Unpublished posts must be posted to a page as the page itself}. El
   * token configurado es el de un usuario del sistema; con él se lee la página y se publica en
   * Instagram, pero no se escribe como la página. El token de página se le pide a la Graph API con
   * el que ya hay.
   */
  @Test
  void escribirEnElMuroVaConElTokenDeLaPagina() throws IOException {
    levantar(intercambio -> responder(intercambio, 200, "{\"id\":\"1\",\"post_id\":\"333_444\"}"));

    cliente().publicar(RedSocial.FACEBOOK, UNA, PIE);

    assertEquals(1, vecesQueSePidioElTokenDePagina);
    assertEquals("Bearer " + TOKEN_DE_PAGINA, autorizaciones.get(0));
  }

  /** Y se pide una sola vez aunque el carrusel sean varias subidas más el post. */
  @Test
  void elTokenDeLaPaginaSePideUnaSolaVezPorCarrusel() throws IOException {
    levantar(intercambio -> responder(intercambio, 200, "{\"id\":\"1\",\"post_id\":\"333_444\"}"));

    cliente().publicar(RedSocial.FACEBOOK, DOS, PIE);

    assertEquals(3, rutasPedidas.size(), "dos subidas y un post");
    assertEquals(1, vecesQueSePidioElTokenDePagina);
  }

  /**
   * Instagram <b>no</b> usa el de la página: su endpoint cuelga de la cuenta de Instagram y el
   * usuario del sistema sí puede publicar ahí — es lo que se midió el 29 de septiembre.
   */
  @Test
  void instagramSigueConElTokenDelUsuarioDelSistema() throws IOException {
    levantar(
        intercambio -> {
          String ruta = intercambio.getRequestURI().getPath();
          if (ruta.endsWith("/media") || ruta.endsWith("/media_publish")) {
            responder(intercambio, 200, "{\"id\":\"C1\"}");
          } else {
            responder(intercambio, 200, "{\"status_code\":\"FINISHED\"}");
          }
        });

    cliente().publicar(RedSocial.INSTAGRAM, UNA, PIE);

    assertEquals(0, vecesQueSePidioElTokenDePagina);
    assertEquals("Bearer TOKEN", autorizaciones.get(0));
  }

  /**
   * Si la página no contesta su token, se sigue con el que hay. Así el motivo que acaba en la ficha
   * del panel es el que dé Meta al publicar —que dice qué falta— y no un error nuestro sobre un
   * token que quien lo lee no sabe que existe.
   */
  @Test
  void siLaPaginaNoDaSuTokenSeSigueConElDelUsuario() throws IOException {
    servidor = HttpServer.create(new InetSocketAddress(0), 0);
    servidor.createContext(
        "/",
        intercambio -> {
          if (esLaConsultaDelTokenDePagina(intercambio)) {
            responder(intercambio, 403, "{\"error\":{\"message\":\"No\"}}");
            return;
          }
          autorizaciones.add(intercambio.getRequestHeaders().getFirst("Authorization"));
          responder(intercambio, 200, "{\"post_id\":\"333_444\"}");
        });
    servidor.start();

    assertTrue(cliente().publicar(RedSocial.FACEBOOK, UNA, PIE).salioBien());
    assertEquals("Bearer TOKEN", autorizaciones.get(0));
  }

  /**
   * El token va en la cabecera: una URL con el token dentro acaba en cualquier registro.
   *
   * <p>En el muro el que viaja es el de la página (ver arriba); lo que esta prueba fija es
   * <b>dónde</b> viaja, no cuál.
   */
  @Test
  void elTokenViajaEnLaCabeceraYNoEnLaUrl() throws IOException {
    levantar(intercambio -> responder(intercambio, 200, "{\"id\":\"1\"}"));

    cliente().publicar(RedSocial.FACEBOOK, UNA, PIE);

    assertEquals("Bearer " + TOKEN_DE_PAGINA, autorizaciones.get(0));
    assertFalse(rutasPedidas.get(0).contains(TOKEN_DE_PAGINA), rutasPedidas.get(0));
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
   * Instagram pide una sola proporción para todo el carrusel: la de su primera foto.
   *
   * <p>No es solo que rechace lo que se sale de 4:5 a 1,91:1 — en un carrusel <b>recorta las demás
   * a la proporción de la primera</b>. Mandarlas distintas significa que Instagram corta por su
   * cuenta justo lo que el ajustador tiene cuidado de no cortar.
   */
  @Test
  void instagramPideLaProporcionDeSuPrimeraFoto() throws IOException {
    levantar(intercambio -> responder(intercambio, 200, "{\"id\":\"1\"}"));
    List<ImagenAPublicar> imagenes =
        List.of(
            new ImagenAPublicar("https://b/uno.jpg", 1183, 1280),
            new ImagenAPublicar("https://b/dos.jpg", 600, 1200));

    assertEquals(
        1183d / 1280, cliente().proporcionDelCarrusel(RedSocial.INSTAGRAM, imagenes).getAsDouble());
  }

  /** Si la primera también se sale del rango, la proporción se lleva al borde admitido. */
  @Test
  void unaPrimeraFueraDeRangoSeLlevaAlBorde() throws IOException {
    levantar(intercambio -> responder(intercambio, 200, "{\"id\":\"1\"}"));
    List<ImagenAPublicar> muyAlta = List.of(new ImagenAPublicar("https://b/alta.jpg", 600, 1200));

    assertEquals(
        0.8, cliente().proporcionDelCarrusel(RedSocial.INSTAGRAM, muyAlta).getAsDouble(), 0.0001);
  }

  /** Facebook no pide ninguna: acepta cualquier mezcla y encajar sería gastar objetos de más. */
  @Test
  void facebookNoPideNingunaProporcion() throws IOException {
    levantar(intercambio -> responder(intercambio, 200, "{\"id\":\"1\"}"));

    assertTrue(cliente().proporcionDelCarrusel(RedSocial.FACEBOOK, DOS).isEmpty());
  }

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

  /**
   * Levanta el servidor falso. <b>La consulta del token de la página la contesta él</b>, antes de
   * pasarle nada al manejador de cada prueba, y no cuenta en {@code rutasPedidas}: es un viaje de
   * infraestructura que ninguna prueba de protocolo quiere ver, y contarlo obligaría a sumarle uno
   * a cada aserción sobre las rutas. Lo que sí se comprueba, en su propia prueba, es que se pide y
   * que el token que devuelve es el que se usa para escribir en el muro.
   */
  private void levantar(Manejador manejador) throws IOException {
    servidor = HttpServer.create(new InetSocketAddress(0), 0);
    servidor.createContext(
        "/",
        intercambio -> {
          if (esLaConsultaDelTokenDePagina(intercambio)) {
            vecesQueSePidioElTokenDePagina++;
            responder(intercambio, 200, "{\"access_token\":\"" + TOKEN_DE_PAGINA + "\"}");
            return;
          }
          rutasPedidas.add(intercambio.getRequestURI().getPath());
          autorizaciones.add(intercambio.getRequestHeaders().getFirst("Authorization"));
          cuerposRecibidos.add(
              new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          manejador.atender(intercambio);
        });
    servidor.start();
  }

  private static boolean esLaConsultaDelTokenDePagina(HttpExchange intercambio) {
    return "GET".equals(intercambio.getRequestMethod())
        && String.valueOf(intercambio.getRequestURI().getQuery()).contains("access_token");
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
