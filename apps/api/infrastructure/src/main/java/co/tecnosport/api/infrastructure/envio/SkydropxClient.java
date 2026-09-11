package co.tecnosport.api.infrastructure.envio;

import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.envio.CotizacionEnvio;
import co.tecnosport.api.application.envio.CotizadorEnvio;
import co.tecnosport.api.domain.envio.TarifaEnvio;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Cliente de Skydropx para la cotización de envío (adr/0021). Implementa {@link CotizadorEnvio}.
 *
 * <p><strong>Lo verificado</strong> (docs/13-skydropx-capacidades.md): OAuth 2.0 con credenciales
 * de cliente contra {@code POST /api/v1/oauth/token}, token de 2 horas, límite de 2 peticiones por
 * segundo; y la cotización asíncrona — {@code POST /api/v1/quotations} crea, {@code GET
 * /api/v1/quotations/{id}} se sondea hasta {@code is_completed}, las tarifas valen 24 horas.
 *
 * <p><strong>Lo que falta</strong> es el mapeo de campos, y está detrás de {@link
 * MapeadorCotizacionSkydropx} con su motivo escrito. Mientras no se confirme, la cotización falla
 * cerrado: lista vacía, que para el checkout es "solo recogida en el punto".
 *
 * <p>La petición del token va <em>form-encoded</em> porque es lo que manda el RFC 6749 §4.4.2 para
 * credenciales de cliente. La documentación de Skydropx nombra los tres parámetros pero no dice la
 * codificación; se sigue el estándar, y si el proveedor se desvía, se desvía él. {@code TODO:
 * confirmar en el panel que el token acepta form-encoded y no JSON.}
 *
 * <p>El token se renueva con margen y no justo al vencer: una cotización que arranca con el token
 * al filo se quedaría a medias entre la creación y el primer sondeo.
 */
public final class SkydropxClient implements CotizadorEnvio {

  private static final Duration TIMEOUT_HTTP = Duration.ofSeconds(10);

  /**
   * Margen para renovar el token antes de que expire. Con 2 horas de vida, 5 minutos cubren de
   * sobra una cotización entera sin gastar tokens de más.
   */
  private static final Duration MARGEN_RENOVACION = Duration.ofMinutes(5);

  /** Verificado: la API acepta hasta 2 peticiones por segundo. */
  private static final int PETICIONES_POR_SEGUNDO = 2;

  private final URI urlBase;
  private final String clientId;
  private final String clientSecret;
  private final OrigenDespacho origen;
  private final Duration topeDeSondeo;
  private final int intentosDeSondeo;
  private final Duration intervaloDeSondeo;
  private final Reloj reloj;
  private final MapeadorCotizacionSkydropx mapeador;
  private final LimitadorDePeticiones limitador;
  private final LimitadorDePeticiones.Pausador pausador;
  private final HttpClient httpClient;
  private final JsonMapper json = JsonMapper.builder().build();

  private String tokenVigente;
  private Instant tokenVenceEn;

  public SkydropxClient(
      URI urlBase,
      String clientId,
      String clientSecret,
      OrigenDespacho origen,
      Duration topeDeSondeo,
      int intentosDeSondeo,
      Duration intervaloDeSondeo,
      Reloj reloj) {
    this(
        urlBase,
        clientId,
        clientSecret,
        origen,
        topeDeSondeo,
        intentosDeSondeo,
        intervaloDeSondeo,
        reloj,
        new MapeadorCotizacionPendiente(),
        LimitadorDePeticiones.deSegundo(PETICIONES_POR_SEGUNDO),
        Thread::sleep,
        HttpClient.newHttpClient());
  }

  /** Punto de extensión para pruebas: servidor local, mapeador de prueba y pausas observables. */
  SkydropxClient(
      URI urlBase,
      String clientId,
      String clientSecret,
      OrigenDespacho origen,
      Duration topeDeSondeo,
      int intentosDeSondeo,
      Duration intervaloDeSondeo,
      Reloj reloj,
      MapeadorCotizacionSkydropx mapeador,
      LimitadorDePeticiones limitador,
      LimitadorDePeticiones.Pausador pausador,
      HttpClient httpClient) {
    this.urlBase = Objects.requireNonNull(urlBase, "La URL base de Skydropx no puede ser nula.");
    this.clientId = exigir(clientId, "El client id de Skydropx");
    this.clientSecret = exigir(clientSecret, "El client secret de Skydropx");
    this.origen = Objects.requireNonNull(origen, "El origen de despacho no puede ser nulo.");
    this.topeDeSondeo = Objects.requireNonNull(topeDeSondeo);
    if (intentosDeSondeo <= 0) {
      throw new IllegalArgumentException(
          "Los intentos de sondeo deben ser mayores que cero: " + intentosDeSondeo);
    }
    this.intentosDeSondeo = intentosDeSondeo;
    this.intervaloDeSondeo = Objects.requireNonNull(intervaloDeSondeo);
    this.reloj = Objects.requireNonNull(reloj);
    this.mapeador = Objects.requireNonNull(mapeador);
    this.limitador = Objects.requireNonNull(limitador);
    this.pausador = Objects.requireNonNull(pausador);
    this.httpClient = Objects.requireNonNull(httpClient);
  }

  /**
   * Ninguna excepción sale de aquí. Un fallo de red, un cuerpo ilegible, un token rechazado o un
   * mapeo sin confirmar terminan igual: sin tarifas. El checkout no distingue entre esos casos
   * porque hace lo mismo en todos — ofrecer la recogida en el punto (adr/0021).
   */
  @Override
  public List<TarifaEnvio> cotizar(CotizacionEnvio cotizacion) {
    Objects.requireNonNull(cotizacion, "La cotización no puede ser nula.");
    try {
      return cotizarOFallarCerrado(cotizacion);
    } catch (MapeoSinConfirmarException | IOException | JacksonException e) {
      return List.of();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return List.of();
    }
  }

  private List<TarifaEnvio> cotizarOFallarCerrado(CotizacionEnvio cotizacion)
      throws IOException, InterruptedException {
    String token = token();
    if (token == null) {
      return List.of();
    }

    String cuerpo = mapeador.cuerpoDeCotizacion(cotizacion, origen);
    HttpResponse<String> creacion =
        enviar(
            peticion("/api/v1/quotations", token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(cuerpo, StandardCharsets.UTF_8))
                .build());
    if (creacion.statusCode() / 100 != 2) {
      return List.of();
    }

    Optional<String> id = mapeador.idDeCotizacion(json.readTree(creacion.body()));
    if (id.isEmpty()) {
      return List.of();
    }

    return sondear(id.get(), token);
  }

  /**
   * El sondeo está acotado por las dos cosas a la vez: número de intentos y tiempo de pared. Solo
   * los intentos no bastan —si cada uno tarda, se acumulan y el comprador queda mirando una
   * pantalla quieta—, y solo el tiempo tampoco —un proveedor que responde al instante haría cientos
   * de llamadas contra un límite de 2 por segundo.
   *
   * <p>Agotado cualquiera de los dos, es cotización fallida. No se espera "un poco más".
   */
  private List<TarifaEnvio> sondear(String idCotizacion, String token)
      throws IOException, InterruptedException {
    Instant limite = reloj.ahora().plus(topeDeSondeo);

    for (int intento = 0; intento < intentosDeSondeo; intento++) {
      if (intento > 0) {
        pausador.pausar(intervaloDeSondeo);
      }
      if (!reloj.ahora().isBefore(limite)) {
        return List.of();
      }

      HttpResponse<String> respuesta =
          enviar(peticion("/api/v1/quotations/" + idCotizacion, token).GET().build());
      if (respuesta.statusCode() / 100 != 2) {
        return List.of();
      }

      Optional<List<TarifaEnvio>> tarifas =
          mapeador.tarifasSiCompleto(json.readTree(respuesta.body()), reloj.ahora());
      if (tarifas.isPresent()) {
        return tarifas.get();
      }
    }
    return List.of();
  }

  /**
   * El token en caché. Se pide uno nuevo solo si no hay o si el que hay está por vencer; Skydropx
   * los da con 2 horas de vida y pedir uno por cotización sería gastar una de las 2 peticiones por
   * segundo en algo que ya se tiene.
   */
  private synchronized String token() throws IOException, InterruptedException {
    Instant ahora = reloj.ahora();
    if (tokenVigente != null
        && tokenVenceEn != null
        && ahora.plus(MARGEN_RENOVACION).isBefore(tokenVenceEn)) {
      return tokenVigente;
    }

    String cuerpo =
        formulario(
            "grant_type", "client_credentials",
            "client_id", clientId,
            "client_secret", clientSecret);
    HttpResponse<String> respuesta =
        enviar(
            HttpRequest.newBuilder()
                .uri(urlBase.resolve("/api/v1/oauth/token"))
                .timeout(TIMEOUT_HTTP)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(cuerpo, StandardCharsets.UTF_8))
                .build());
    if (respuesta.statusCode() / 100 != 2) {
      invalidarToken();
      return null;
    }

    JsonNode raiz = json.readTree(respuesta.body());
    String acceso = raiz.path("access_token").asString();
    if (acceso == null || acceso.isBlank()) {
      invalidarToken();
      return null;
    }
    long viveSegundos = raiz.path("expires_in").asLong(0);
    if (viveSegundos <= 0) {
      // Sin expires_in no se puede saber cuánto dura: se usa una vez y no se cachea. Cachearlo
      // "por si acaso" con una duración supuesta es peor que pedir uno nuevo cada vez.
      invalidarToken();
      return acceso;
    }
    tokenVigente = acceso;
    tokenVenceEn = reloj.ahora().plusSeconds(viveSegundos);
    return acceso;
  }

  private void invalidarToken() {
    tokenVigente = null;
    tokenVenceEn = null;
  }

  private HttpRequest.Builder peticion(String ruta, String token) {
    return HttpRequest.newBuilder()
        .uri(urlBase.resolve(ruta))
        .timeout(TIMEOUT_HTTP)
        .header("Authorization", "Bearer " + token)
        .header("Accept", "application/json");
  }

  private HttpResponse<String> enviar(HttpRequest peticion)
      throws IOException, InterruptedException {
    limitador.esperarTurno();
    return httpClient.send(peticion, HttpResponse.BodyHandlers.ofString());
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

  private static String exigir(String valor, String queEs) {
    if (valor == null || valor.isBlank()) {
      throw new IllegalArgumentException(queEs + " no puede estar vacío.");
    }
    return valor;
  }
}
