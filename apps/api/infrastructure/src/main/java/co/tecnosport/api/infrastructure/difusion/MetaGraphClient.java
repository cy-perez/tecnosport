package co.tecnosport.api.infrastructure.difusion;

import co.tecnosport.api.application.difusion.ImagenAPublicar;
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
import java.util.ArrayList;
import java.util.List;
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
 * <h2>Y con varias fotos, cada una por su lado otra vez</h2>
 *
 * <p>Desde el 6 de octubre de 2026 se publica el carrusel entero de la ficha. Las dos redes lo
 * hacen distinto, y ninguna de las dos formas se parece a la de una foto suelta:
 *
 * <ul>
 *   <li><b>Facebook</b>: cada foto se sube a {@code /photos} con {@code published=false}, que
 *       devuelve un id, y luego un solo {@code /feed} con el mensaje y los ids en {@code
 *       attached_media}. Subirlas publicadas dejaría N posts de una foto en vez de uno con N.
 *   <li><b>Instagram</b>: un contenedor hijo por foto con {@code is_carousel_item=true}, un
 *       contenedor padre con {@code media_type=CAROUSEL} y los hijos en {@code children}, y el
 *       {@code media_publish} del padre. Se sondea el <b>padre</b>, que es el que agrupa el
 *       procesamiento de todos.
 * </ul>
 *
 * <p>Con una sola foto se usa el camino de siempre en las dos: un carrusel de uno no existe en
 * Instagram —el padre lo rechaza— y en Facebook sería un post peor a cambio de nada.
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

  /** El token de la página, resuelto la primera vez que hace falta. Ver {@code tokenDeLaPagina}. */
  private volatile String tokenDePagina;

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

  /**
   * Cuántas fotos caben en un carrusel de Instagram. Es un número de Meta, igual que el tope de
   * caracteres del pie: por eso vive aquí y no en el agregado ({@code PublicacionEnRed} lo razona).
   * Facebook admite más, pero diez ya son más de las que nadie desliza.
   */
  private static final int TOPE_DEL_CARRUSEL = 10;

  /**
   * El rango de proporciones que Instagram acepta: de 4:5 (0,8 — vertical) a 1,91:1 (apaisada).
   *
   * <p>Una foto fuera de ese rango deja el contenedor en {@code ERROR}, y en un carrusel eso tumba
   * el post entero por una sola foto. Por eso se descartan <b>antes</b> de crear nada, y por eso el
   * puerto tiene un {@code admitidasPor}: el caso de uso necesita saber cuáles quedaron para
   * guardar en la constancia lo que de verdad salió.
   *
   * <p>Facebook no tiene este límite: recorta o enmarca lo que le llegue.
   */
  private static final double PROPORCION_MINIMA_INSTAGRAM = 0.8;

  private static final double PROPORCION_MAXIMA_INSTAGRAM = 1.91;

  @Override
  public List<ImagenAPublicar> admitidasPor(RedSocial red, List<ImagenAPublicar> imagenes) {
    List<ImagenAPublicar> admitidas =
        switch (red) {
          case FACEBOOK -> List.copyOf(imagenes);
          case INSTAGRAM -> imagenes.stream().filter(MetaGraphClient::cabeEnInstagram).toList();
        };
    if (admitidas.size() > TOPE_DEL_CARRUSEL) {
      admitidas = List.copyOf(admitidas.subList(0, TOPE_DEL_CARRUSEL));
    }
    if (admitidas.size() < imagenes.size()) {
      log.info(
          "De las {} fotos ofrecidas, en {} salen {}: el resto no cumple sus límites de proporción"
              + " o no cabe en el carrusel.",
          imagenes.size(),
          red,
          admitidas.size());
    }
    return admitidas;
  }

  private static boolean cabeEnInstagram(ImagenAPublicar imagen) {
    double proporcion = imagen.proporcion();
    return proporcion >= PROPORCION_MINIMA_INSTAGRAM && proporcion <= PROPORCION_MAXIMA_INSTAGRAM;
  }

  @Override
  public ResultadoPublicacion publicar(
      RedSocial red, List<ImagenAPublicar> imagenes, String pieDeFoto) {
    if (imagenes.isEmpty()) {
      return ResultadoPublicacion.fallida("No se mandó ninguna imagen que publicar.");
    }
    try {
      return switch (red) {
        case FACEBOOK -> publicarEnFacebook(imagenes, pieDeFoto);
        case INSTAGRAM -> publicarEnInstagram(imagenes, pieDeFoto);
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

  private ResultadoPublicacion publicarEnFacebook(List<ImagenAPublicar> imagenes, String pieDeFoto)
      throws IOException, InterruptedException {
    return imagenes.size() == 1
        ? publicarUnaEnFacebook(imagenes.get(0).url(), pieDeFoto)
        : publicarVariasEnFacebook(imagenes, pieDeFoto);
  }

  /**
   * Una sola llamada. Se usa {@code /photos} y no {@code /feed} porque lo que se publica es una
   * foto con pie: por {@code /feed} con un enlace, Facebook decide él la miniatura a partir de las
   * etiquetas {@code og:} de la ficha, y entonces la imagen del post deja de ser la que se eligió.
   */
  private ResultadoPublicacion publicarUnaEnFacebook(String urlImagen, String pieDeFoto)
      throws IOException, InterruptedException {
    HttpResponse<String> respuesta =
        postearComoLaPagina(
            "/" + pageId + "/photos", formulario("url", urlImagen, "caption", pieDeFoto));

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

  /**
   * Primero las fotos sin publicar, después el post que las lleva.
   *
   * <p>{@code published=false} es lo único que separa esto de N posts sueltos: una foto subida así
   * queda en la página sin asomar al muro, y el {@code /feed} de después la reclama por su id en
   * {@code attached_media}. Si una subida falla no se sigue: el post saldría con menos fotos de las
   * que alguien eligió y sin decirlo en ninguna parte.
   *
   * <p>Las que ya se subieron se quedan ahí, sin publicar, y no se borran. Es deliberado, y es la
   * misma postura del informe de huérfanos del bucket: borrar dentro de un camino de error es donde
   * se cometen los errores caros, y una foto sin publicar no la ve nadie.
   */
  private ResultadoPublicacion publicarVariasEnFacebook(
      List<ImagenAPublicar> imagenes, String pieDeFoto) throws IOException, InterruptedException {
    List<String> idsDeFoto = new ArrayList<>();
    for (ImagenAPublicar imagen : imagenes) {
      HttpResponse<String> subida =
          postearComoLaPagina(
              "/" + pageId + "/photos", formulario("url", imagen.url(), "published", "false"));
      if (noEsExitosa(subida)) {
        return ResultadoPublicacion.fallida(motivoDe(subida));
      }
      String idDeFoto = json.readTree(subida.body()).path("id").asString();
      if (idDeFoto == null || idDeFoto.isBlank()) {
        return ResultadoPublicacion.fallida("Facebook subió una foto sin devolver su id.");
      }
      idsDeFoto.add(idDeFoto);
    }

    // `attached_media[i]` con un objeto JSON dentro de un campo de formulario: así lo pide la Graph
    // API, no es una rareza nuestra. `formulario` lo codifica entero.
    List<String> campos = new ArrayList<>(List.of("message", pieDeFoto));
    for (int i = 0; i < idsDeFoto.size(); i++) {
      campos.add("attached_media[" + i + "]");
      campos.add(jsonDeMediaFbid(idsDeFoto.get(i)));
    }

    HttpResponse<String> publicacion =
        postearComoLaPagina("/" + pageId + "/feed", formulario(campos.toArray(String[]::new)));
    if (noEsExitosa(publicacion)) {
      return ResultadoPublicacion.fallida(motivoDe(publicacion));
    }
    JsonNode raiz = json.readTree(publicacion.body());
    String idDelPost = primeroNoVacio(raiz.path("post_id").asString(), raiz.path("id").asString());
    return idDelPost == null
        ? ResultadoPublicacion.fallida("Facebook respondió 200 sin identificador de publicación.")
        : ResultadoPublicacion.publicada(idDelPost);
  }

  private ResultadoPublicacion publicarEnInstagram(List<ImagenAPublicar> imagenes, String pieDeFoto)
      throws IOException, InterruptedException {
    // El contenedor que se va a publicar: el de la foto única, o el padre del carrusel. De ahí en
    // adelante los dos caminos son el mismo — sondear y publicar.
    String contenedor;
    if (imagenes.size() == 1) {
      ResultadoDeContenedor unico =
          crearContenedor(formulario("image_url", imagenes.get(0).url(), "caption", pieDeFoto));
      if (unico.motivoDelFallo() != null) {
        return ResultadoPublicacion.fallida(unico.motivoDelFallo());
      }
      contenedor = unico.id();
    } else {
      ResultadoDeContenedor padre = crearCarrusel(imagenes, pieDeFoto);
      if (padre.motivoDelFallo() != null) {
        return ResultadoPublicacion.fallida(padre.motivoDelFallo());
      }
      contenedor = padre.id();
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
   * Un contenedor hijo por foto y uno padre que los agrupa.
   *
   * <p><b>El pie va en el padre y no en los hijos</b>: un carrusel tiene un solo texto. Y los hijos
   * llevan {@code is_carousel_item=true}, que es lo que impide que Meta los trate como publicables
   * por su cuenta.
   *
   * <p>Solo se sondea el padre, después. Los hijos también se procesan de forma asíncrona, pero el
   * padre no queda {@code FINISHED} hasta que todos lo están, así que sondear los hijos uno a uno
   * sería hacer el mismo trabajo N veces y tardar N veces más en publicar.
   */
  private ResultadoDeContenedor crearCarrusel(List<ImagenAPublicar> imagenes, String pieDeFoto)
      throws IOException, InterruptedException {
    List<String> hijos = new ArrayList<>();
    for (ImagenAPublicar imagen : imagenes) {
      ResultadoDeContenedor hijo =
          crearContenedor(formulario("image_url", imagen.url(), "is_carousel_item", "true"));
      if (hijo.motivoDelFallo() != null) {
        return hijo;
      }
      hijos.add(hijo.id());
    }
    return crearContenedor(
        formulario(
            "media_type", "CAROUSEL", "children", String.join(",", hijos), "caption", pieDeFoto));
  }

  /** Crea un contenedor de Instagram con los campos que se le den y devuelve su id, o el motivo. */
  private ResultadoDeContenedor crearContenedor(String cuerpo)
      throws IOException, InterruptedException {
    HttpResponse<String> respuesta = postear("/" + igUserId + "/media", cuerpo);
    if (noEsExitosa(respuesta)) {
      return new ResultadoDeContenedor(null, motivoDe(respuesta));
    }
    String id = json.readTree(respuesta.body()).path("id").asString();
    return id == null || id.isBlank()
        ? new ResultadoDeContenedor(null, "Instagram no devolvió el id del contenedor.")
        : new ResultadoDeContenedor(id, null);
  }

  /** El id del contenedor, o el motivo por el que no se pudo crear. Nunca los dos. */
  private record ResultadoDeContenedor(String id, String motivoDelFallo) {}

  /**
   * El objeto que Facebook espera dentro de cada {@code attached_media[i]}: {@code
   * {"media_fbid":"..."}}. Se arma a mano y no con Jackson porque es un literal de dos campos
   * fijos, y el id que va dentro lo acaba de emitir Meta — dígitos, nada que escapar.
   */
  private static String jsonDeMediaFbid(String idDeFoto) {
    return "{\"media_fbid\":\"" + idDeFoto + "\"}";
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
    return peticion(ruta, token);
  }

  private HttpRequest.Builder peticion(String ruta, String tokenDeLaPeticion) {
    return HttpRequest.newBuilder()
        .uri(urlBase.resolve(ruta))
        .timeout(timeoutHttp)
        .header("Authorization", "Bearer " + tokenDeLaPeticion)
        .header("Accept", "application/json");
  }

  /**
   * El token <b>de la página</b>, que no es el que trae la configuración.
   *
   * <p>Lo que hay configurado es el token de un usuario del sistema. Con él se puede leer la página
   * y publicar en Instagram, pero <b>no escribir en el muro como la página</b>: Meta contesta
   * {@code (#200) Unpublished posts must be posted to a page as the page itself}. Medido contra la
   * cuenta real el 7 de octubre de 2026, al publicar el primer carrusel de verdad — la comprobación
   * del 29 de septiembre solo había creado un contenedor de Instagram, así que este camino nunca se
   * había ejercido.
   *
   * <p>Se pide a la Graph API en vez de configurarse aparte, que era la otra vía: un token de
   * página más en Secret Manager son dos secretos que caducan por separado y que alguien tiene que
   * acordarse de rotar juntos. Este sale del que ya hay, y si el de la página cambia porque
   * cambiaron los permisos, se recoge solo en el siguiente arranque.
   *
   * <p>Se guarda en memoria tras la primera vez: es el mismo para toda la vida del proceso y
   * pedirlo en cada foto de un carrusel serían cinco viajes de más. {@code volatile} y sin
   * sincronizar porque dos hilos que lo pidan a la vez obtienen el mismo valor — lo peor que pasa
   * es una petición repetida.
   */
  private String tokenDeLaPagina() throws IOException, InterruptedException {
    String enCache = tokenDePagina;
    if (enCache != null) {
      return enCache;
    }
    HttpResponse<String> respuesta =
        httpClient.send(
            peticion("/" + pageId + "?fields=access_token").GET().build(),
            HttpResponse.BodyHandlers.ofString());
    if (noEsExitosa(respuesta)) {
      // Se sigue con el del usuario del sistema en vez de cortar aquí: así el motivo que acaba en
      // la ficha del panel es el que dé Meta al publicar, que dice qué falta, y no un error
      // nuestro sobre un token que quien lo lee no sabe que existe.
      log.warn("No se pudo obtener el token de la página: {}", motivoDe(respuesta));
      return token;
    }
    String deLaPagina = json.readTree(respuesta.body()).path("access_token").asString();
    if (deLaPagina == null || deLaPagina.isBlank()) {
      log.warn("La Graph API devolvió la página sin token de acceso.");
      return token;
    }
    tokenDePagina = deLaPagina;
    return deLaPagina;
  }

  /** Igual que {@link #postear}, pero como la página y no como el usuario del sistema. */
  private HttpResponse<String> postearComoLaPagina(String ruta, String cuerpo)
      throws IOException, InterruptedException {
    return httpClient.send(
        peticion(ruta, tokenDeLaPagina())
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(cuerpo, StandardCharsets.UTF_8))
            .build(),
        HttpResponse.BodyHandlers.ofString());
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
