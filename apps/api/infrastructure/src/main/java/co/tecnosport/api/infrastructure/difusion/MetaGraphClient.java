package co.tecnosport.api.infrastructure.difusion;

import co.tecnosport.api.application.difusion.PublicadorEnRedSocial;
import co.tecnosport.api.application.difusion.ResultadoPublicacion;
import co.tecnosport.api.domain.difusion.RedSocial;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Publica en la página de Facebook y en la cuenta de Instagram del negocio, por la Graph API.
 *
 * <p><strong>Los dos en la misma clase, y no es por comodidad</strong>: comparten el token, el host
 * y el cliente HTTP, y sobre todo comparten cuenta — las dos publicaciones salen del mismo usuario
 * del sistema y cuentan contra los mismos límites de Meta. Es el mismo razonamiento que sostiene a
 * {@code SkydropxClient} con sus cinco puertos.
 *
 * <p><strong>Lo verificado contra la cuenta real el 29 de septiembre de 2026</strong>, no leído:
 * usuario del sistema con token permanente ({@code expires_at: 0}) y los seis permisos concedidos
 * —{@code pages_show_list}, {@code business_management}, {@code instagram_basic}, {@code
 * instagram_content_publish}, {@code pages_read_engagement}, {@code pages_manage_posts}—; la página
 * y la cuenta de Instagram vinculadas; y un contenedor de Instagram creado de verdad contra una
 * vista previa del bucket, que Meta descargó y dejó en {@code FINISHED} sin publicarlo. Eso último
 * es lo que demostró que <b>el Acceso estándar basta y no hace falta App Review</b>.
 *
 * <h2>Facebook es un viaje; Instagram son tres</h2>
 *
 * <p>Facebook acepta la foto y el pie de una vez. Instagram no: primero se crea un contenedor, que
 * Meta procesa de forma asíncrona descargando la imagen, y solo cuando ese contenedor está {@code
 * FINISHED} se publica. Entre medias hay que sondear. Sin el sondeo, publicar un contenedor todavía
 * en {@code IN_PROGRESS} devuelve un error que parece de permisos y no lo es.
 *
 * <h2>Por qué casi nada de aquí lanza</h2>
 *
 * <p>El puerto promete devolver el rechazo como resultado, no como excepción, y aquí se cumple
 * hasta el final: un 400 de Meta, un contenedor en {@code ERROR} o un sondeo que se agota salen
 * como {@link ResultadoPublicacion#fallida}. El motivo acaba en la ficha del panel, que es donde
 * alguien puede leerlo y decidir. Solo se deja escapar lo que impide siquiera hablar con Meta.
 */
public final class MetaGraphClient implements PublicadorEnRedSocial {

  private static final Logger log = LoggerFactory.getLogger(MetaGraphClient.class);

  private final HttpClient httpClient;
  private final JsonMapper json = JsonMapper.builder().build();
  private final URI urlBase;
  private final String pageId;
  private final String igUserId;
  private final String token;
  private final Duration timeoutHttp;
  private final int sondeosDelContenedor;
  private final Duration esperaEntreSondeos;

  public MetaGraphClient(
      URI urlBase,
      String pageId,
      String igUserId,
      String token,
      Duration timeoutHttp,
      int sondeosDelContenedor,
      Duration esperaEntreSondeos) {
    this.urlBase = Objects.requireNonNull(urlBase, "La URL base de la Graph API es obligatoria.");
    this.pageId = Objects.requireNonNull(pageId, "El id de la página es obligatorio.");
    this.igUserId = Objects.requireNonNull(igUserId, "El id de la cuenta de Instagram lo es.");
    this.token = Objects.requireNonNull(token, "El token es obligatorio.");
    this.timeoutHttp = Objects.requireNonNull(timeoutHttp, "El timeout es obligatorio.");
    this.sondeosDelContenedor = sondeosDelContenedor;
    this.esperaEntreSondeos = Objects.requireNonNull(esperaEntreSondeos, "La espera lo es.");
    this.httpClient = HttpClient.newBuilder().connectTimeout(timeoutHttp).build();
  }

  @Override
  public ResultadoPublicacion publicar(RedSocial red, String urlImagen, String pieDeFoto) {
    try {
      return switch (red) {
        case FACEBOOK -> publicarEnFacebook(urlImagen, pieDeFoto);
        case INSTAGRAM -> publicarEnInstagram(urlImagen, pieDeFoto);
      };
    } catch (InterruptedException e) {
      // Restaurar la bandera y no tragársela: quien interrumpió quiere que el hilo termine.
      Thread.currentThread().interrupt();
      return ResultadoPublicacion.fallida("La publicación se interrumpió antes de terminar.");
    } catch (IOException e) {
      log.warn("No se pudo hablar con la Graph API para publicar en {}", red, e);
      return ResultadoPublicacion.fallida("No se pudo contactar a Meta: " + e.getMessage());
    }
  }

  /**
   * Una sola llamada. Se usa {@code /photos} y no {@code /feed} porque lo que se publica es una
   * foto con pie: por {@code /feed} con un enlace, Facebook decide él la miniatura a partir de las
   * etiquetas {@code og:} de la ficha, y entonces la imagen del post deja de ser la que se eligió.
   */
  private ResultadoPublicacion publicarEnFacebook(String urlImagen, String pieDeFoto)
      throws IOException, InterruptedException {
    HttpResponse<String> respuesta =
        postear("/" + pageId + "/photos", formulario("url", urlImagen, "caption", pieDeFoto));

    if (noEsExitosa(respuesta)) {
      return ResultadoPublicacion.fallida(motivoDe(respuesta));
    }
    JsonNode raiz = json.readTree(respuesta.body());
    // `post_id` es el de la publicación en el muro; `id` es el de la foto. El que sirve para volver
    // al post es el primero, y solo cuando falta se cae al segundo.
    String id = primeroNoVacio(raiz.path("post_id").asString(), raiz.path("id").asString());
    return id == null
        ? ResultadoPublicacion.fallida("Facebook respondió 200 sin identificador de publicación.")
        : ResultadoPublicacion.publicada(id);
  }

  private ResultadoPublicacion publicarEnInstagram(String urlImagen, String pieDeFoto)
      throws IOException, InterruptedException {
    HttpResponse<String> creacion =
        postear(
            "/" + igUserId + "/media", formulario("image_url", urlImagen, "caption", pieDeFoto));
    if (noEsExitosa(creacion)) {
      return ResultadoPublicacion.fallida(motivoDe(creacion));
    }
    String contenedor = json.readTree(creacion.body()).path("id").asString();
    if (contenedor == null || contenedor.isBlank()) {
      return ResultadoPublicacion.fallida("Instagram no devolvió el id del contenedor.");
    }

    String problema = esperarAQueElContenedorEsteListo(contenedor);
    if (problema != null) {
      return ResultadoPublicacion.fallida(problema);
    }

    HttpResponse<String> publicacion =
        postear("/" + igUserId + "/media_publish", formulario("creation_id", contenedor));
    if (noEsExitosa(publicacion)) {
      return ResultadoPublicacion.fallida(motivoDe(publicacion));
    }
    String id = json.readTree(publicacion.body()).path("id").asString();
    return id == null || id.isBlank()
        ? ResultadoPublicacion.fallida("Instagram publicó sin devolver identificador.")
        : ResultadoPublicacion.publicada(id);
  }

  /**
   * Sondea el contenedor hasta {@code FINISHED}. Devuelve {@code null} cuando quedó listo, y el
   * motivo cuando no.
   *
   * <p>Los estados son {@code IN_PROGRESS}, {@code FINISHED}, {@code ERROR} y {@code EXPIRED}. El
   * {@code ERROR} trae además {@code status} con la explicación en inglés de Meta, y se propaga tal
   * cual: traducirla a un mensaje propio perdería justo el detalle que hace falta para arreglarlo
   * —qué tenía de malo la imagen—, y quien lo lee es quien administra, no quien compra.
   */
  private String esperarAQueElContenedorEsteListo(String contenedor)
      throws IOException, InterruptedException {
    for (int intento = 0; intento < sondeosDelContenedor; intento++) {
      HttpResponse<String> respuesta =
          httpClient.send(
              peticion("/" + contenedor + "?fields=status_code,status").GET().build(),
              HttpResponse.BodyHandlers.ofString());
      if (noEsExitosa(respuesta)) {
        return motivoDe(respuesta);
      }
      JsonNode raiz = json.readTree(respuesta.body());
      String estado = raiz.path("status_code").asString();
      if ("FINISHED".equals(estado)) {
        return null;
      }
      if ("ERROR".equals(estado) || "EXPIRED".equals(estado)) {
        return "Instagram no pudo preparar la imagen ("
            + estado
            + "): "
            + raiz.path("status").asString();
      }
      Thread.sleep(esperaEntreSondeos.toMillis());
    }
    // Agotar el sondeo no significa que no vaya a publicarse: significa que no nos consta. La fila
    // queda FALLIDA y quien mire la cuenta puede ver si al final salió -- que es más honesto que
    // darlo por publicado sin un identificador con el que comprobarlo.
    return "Instagram no terminó de procesar la imagen a tiempo. Revisa la cuenta antes de"
        + " reintentar: puede haberse publicado igual.";
  }

  private HttpResponse<String> postear(String ruta, String cuerpo)
      throws IOException, InterruptedException {
    return httpClient.send(
        peticion(ruta)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(cuerpo, StandardCharsets.UTF_8))
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }

  /**
   * El token va en la cabecera y no en la cadena de consulta, que es la otra forma que Meta admite:
   * una URL con el token dentro acaba en los registros de acceso, en el historial de reintentos y
   * en cualquier traza que alguien pegue en un ticket.
   */
  private HttpRequest.Builder peticion(String ruta) {
    return HttpRequest.newBuilder()
        .uri(urlBase.resolve(ruta))
        .timeout(timeoutHttp)
        .header("Authorization", "Bearer " + token)
        .header("Accept", "application/json");
  }

  private static boolean noEsExitosa(HttpResponse<String> respuesta) {
    return respuesta.statusCode() / 100 != 2;
  }

  /**
   * El mensaje de error de Meta, que viene en {@code error.message}. Si el cuerpo no es el JSON
   * esperado se cae al código HTTP, porque un motivo vacío en la ficha del panel no ayuda a nadie.
   */
  private String motivoDe(HttpResponse<String> respuesta) {
    try {
      String mensaje = json.readTree(respuesta.body()).path("error").path("message").asString();
      if (mensaje != null && !mensaje.isBlank()) {
        return "Meta respondió " + respuesta.statusCode() + ": " + mensaje;
      }
    } catch (RuntimeException e) {
      log.debug("La respuesta de error de Meta no venía en el JSON esperado", e);
    }
    return "Meta respondió " + respuesta.statusCode() + ".";
  }

  private static String primeroNoVacio(String... candidatos) {
    for (String candidato : candidatos) {
      if (candidato != null && !candidato.isBlank()) {
        return candidato;
      }
    }
    return null;
  }

  private static String formulario(String... clavesYValores) {
    StringBuilder cuerpo = new StringBuilder();
    for (int i = 0; i < clavesYValores.length; i += 2) {
      if (i > 0) {
        cuerpo.append('&');
      }
      cuerpo
          .append(URLEncoder.encode(clavesYValores[i], StandardCharsets.UTF_8))
          .append('=')
          .append(URLEncoder.encode(clavesYValores[i + 1], StandardCharsets.UTF_8));
    }
    return cuerpo.toString();
  }
}
