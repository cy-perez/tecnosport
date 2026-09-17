package co.tecnosport.api.infrastructure.envio;

import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.envio.AplicarEventoDeEnvioComando;
import co.tecnosport.api.application.envio.ConsultorDeSeguimiento;
import co.tecnosport.api.application.envio.CotizacionEnvio;
import co.tecnosport.api.application.envio.CotizadorEnvio;
import co.tecnosport.api.application.envio.EmisorDeGuias;
import co.tecnosport.api.application.envio.LecturaDeEnvioEmitido;
import co.tecnosport.api.application.envio.ResultadoCotizacion;
import co.tecnosport.api.application.envio.ResultadoEmision;
import co.tecnosport.api.application.envio.SolicitudDeEmision;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Cliente de Skydropx: cotiza envíos (adr/0021), emite guías (adr/0033) y consulta el rastreo
 * (adr/0022).
 *
 * <p><strong>Los tres en la misma clase, y no es por comodidad.</strong> El token en caché y el
 * limitador de dos peticiones por segundo son de la <em>cuenta</em>, no de un caso de uso: dos
 * instancias serían dos tokens y dos limitadores contra un único límite, y el segundo no sabría del
 * primero. El tercer tramo —la emisión— llegó el 16 de septiembre de 2026 y entró aquí por lo
 * mismo, que era lo que este javadoc ya decía.
 *
 * <p><strong>Lo verificado</strong> (docs/13-skydropx-capacidades.md): OAuth 2.0 con credenciales
 * de cliente contra {@code POST /api/v1/oauth/token}, token de 2 horas, límite de 2 peticiones por
 * segundo; y la cotización asíncrona — {@code POST /api/v1/quotations} crea, {@code GET
 * /api/v1/quotations/{id}} se sondea hasta {@code is_completed}, las tarifas valen 24 horas.
 *
 * <p><strong>El mapeo de campos</strong> se confirmó contra la cuenta el 11 de septiembre de 2026 y
 * vive en {@link MapeadorCotizacionSkydropxV1}. Sigue detrás de la interfaz porque la frontera
 * sirve igual para probar el protocolo sin depender del proveedor.
 *
 * <p>La petición del token va <em>form-encoded</em>, que es lo que manda el RFC 6749 §4.4.2 para
 * credenciales de cliente. Comprobado contra el sandbox: Skydropx acepta form-encoded y también
 * JSON, y devuelve {@code expires_in: 7200}.
 *
 * <p>El token se renueva con margen y no justo al vencer: una cotización que arranca con el token
 * al filo se quedaría a medias entre la creación y el primer sondeo.
 */
public final class SkydropxClient implements CotizadorEnvio, ConsultorDeSeguimiento, EmisorDeGuias {

  private static final Logger log = LoggerFactory.getLogger(SkydropxClient.class);

  private static final Duration TIMEOUT_HTTP = Duration.ofSeconds(10);

  /**
   * Margen para renovar el token antes de que expire. Con 2 horas de vida, 5 minutos cubren de
   * sobra una cotización entera sin gastar tokens de más.
   */
  private static final Duration MARGEN_RENOVACION = Duration.ofMinutes(5);

  /** Verificado: la API acepta hasta 2 peticiones por segundo. */
  private static final int PETICIONES_POR_SEGUNDO = 2;

  /**
   * Cuántas veces se repite la creación de un envío cuando la llamada se cae a medias. Es seguro
   * <strong>solo</strong> porque el cuerpo lleva {@code unique_shipment: true}: la plataforma
   * cachea la respuesta por {@code rate_id} durante 96 horas y un reintento con la misma tarifa
   * devuelve los mismos envíos en vez de crear otros. Sin esa llave, reintentar un {@code 408}
   * emite dos guías y cobra dos veces — pasó, y salió gratis de milagro (docs/13 §6.2).
   */
  private static final int INTENTOS_DE_EMISION = 3;

  /** Entre reintentos de emisión: un 409 dice "hay una creación en proceso", y hay que dejarla. */
  private static final Duration ESPERA_ENTRE_EMISIONES = Duration.ofSeconds(3);

  private final URI urlBase;
  private final String clientId;
  private final String clientSecret;
  private final OrigenDespacho origen;
  private final Duration topeDeSondeo;
  private final int intentosDeSondeo;
  private final Duration intervaloDeSondeo;
  private final Reloj reloj;
  private final MapeadorCotizacionSkydropx mapeador;
  private final MapeadorSeguimientoSkydropx mapeadorSeguimiento;
  private final MapeadorEmisionSkydropxV2 mapeadorEmision;
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
        new MapeadorCotizacionSkydropxV1(),
        new MapeadorSeguimientoSkydropxV1(),
        new MapeadorEmisionSkydropxV2(),
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
      MapeadorSeguimientoSkydropx mapeadorSeguimiento,
      MapeadorEmisionSkydropxV2 mapeadorEmision,
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
    this.mapeadorSeguimiento = Objects.requireNonNull(mapeadorSeguimiento);
    this.mapeadorEmision = Objects.requireNonNull(mapeadorEmision);
    this.limitador = Objects.requireNonNull(limitador);
    this.pausador = Objects.requireNonNull(pausador);
    this.httpClient = Objects.requireNonNull(httpClient);
  }

  /**
   * Ninguna excepción sale de aquí. Un fallo de red, un cuerpo ilegible, un token rechazado o un
   * mapeo que revienta terminan igual: sin tarifas. El checkout no distingue entre esos casos
   * porque hace lo mismo en todos — ofrecer la recogida en el punto (adr/0021).
   *
   * <p>Se atrapa {@link RuntimeException} entera, y es a propósito: el contrato de este puerto es
   * que cotizar no tumba el checkout, y un campo inesperado en la respuesta del proveedor no puede
   * dejar sin comprar a nadie. Lo que se pierde —un fallo de programación que pasa desapercibido—
   * lo cubren las pruebas del mapeador, que sí ven la excepción.
   */
  @Override
  public ResultadoCotizacion cotizar(CotizacionEnvio cotizacion) {
    Objects.requireNonNull(cotizacion, "La cotización no puede ser nula.");
    try {
      return cotizarOFallarCerrado(cotizacion);
    } catch (IOException | RuntimeException e) {
      // El mensaje de la excepción y no la traza: esto es un proveedor caído o una respuesta con
      // otra forma, no un fallo nuestro que haya que depurar por la pila.
      return fallo(ResultadoCotizacion.Motivo.PROVEEDOR_NO_DISPONIBLE, e.toString());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return fallo(ResultadoCotizacion.Motivo.PROVEEDOR_NO_DISPONIBLE, "hilo interrumpido");
    }
  }

  private ResultadoCotizacion cotizarOFallarCerrado(CotizacionEnvio cotizacion)
      throws IOException, InterruptedException {
    String token = token();
    if (token == null) {
      return fallo(
          ResultadoCotizacion.Motivo.SIN_CREDENCIALES,
          "no se obtuvo token; revisar SKYDROPX_CLIENT_ID y SKYDROPX_CLIENT_SECRET");
    }

    String cuerpo = mapeador.cuerpoDeCotizacion(cotizacion, origen);
    HttpResponse<String> creacion =
        enviar(
            peticion("/api/v1/quotations", token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(cuerpo, StandardCharsets.UTF_8))
                .build());
    if (creacion.statusCode() / 100 != 2) {
      return fallo(
          ResultadoCotizacion.Motivo.PROVEEDOR_NO_DISPONIBLE,
          "la creacion de la cotizacion respondio " + creacion.statusCode());
    }

    Optional<String> id = mapeador.idDeCotizacion(json.readTree(creacion.body()));
    if (id.isEmpty()) {
      return fallo(
          ResultadoCotizacion.Motivo.RESPUESTA_INESPERADA,
          "la cotizacion creada llego sin identificador");
    }

    return sondear(id.get(), token);
  }

  /**
   * El registro de un fallo de cotizacion, en un solo sitio. Va en {@code warn} y no en {@code
   * error}: no hay nada roto de nuestro lado y el checkout sigue vendiendo con recogida en el
   * punto, pero si esto sale seguido alguien tiene que mirarlo.
   *
   * <p><strong>Lo que no entra en el registro</strong>: la direccion de destino ni nada del
   * comprador. Un fallo de cotizacion se diagnostica con el motivo y, cuando existe, con el
   * identificador de la cotizacion en la plataforma.
   */
  private ResultadoCotizacion fallo(ResultadoCotizacion.Motivo motivo, String detalle) {
    log.warn("No se pudo cotizar el envio ({}): {}", motivo, detalle);
    return new ResultadoCotizacion.NoSePudoCotizar(motivo);
  }

  /**
   * El sondeo está acotado por las dos cosas a la vez: número de intentos y tiempo de pared. Solo
   * los intentos no bastan —si cada uno tarda, se acumulan y el comprador queda mirando una
   * pantalla quieta—, y solo el tiempo tampoco —un proveedor que responde al instante haría cientos
   * de llamadas contra un límite de 2 por segundo.
   *
   * <p>Agotado cualquiera de los dos, es cotización fallida. No se espera "un poco más".
   */
  private ResultadoCotizacion sondear(String idCotizacion, String token)
      throws IOException, InterruptedException {
    Instant limite = reloj.ahora().plus(topeDeSondeo);

    for (int intento = 0; intento < intentosDeSondeo; intento++) {
      if (intento > 0) {
        pausador.pausar(intervaloDeSondeo);
      }
      if (!reloj.ahora().isBefore(limite)) {
        return sondeoAgotado(idCotizacion, "se agoto el tiempo");
      }

      HttpResponse<String> respuesta =
          enviar(peticion("/api/v1/quotations/" + idCotizacion, token).GET().build());
      if (respuesta.statusCode() / 100 != 2) {
        return fallo(
            ResultadoCotizacion.Motivo.PROVEEDOR_NO_DISPONIBLE,
            "el sondeo de la cotizacion " + idCotizacion + " respondio " + respuesta.statusCode());
      }

      Optional<List<TarifaEnvio>> tarifas =
          mapeador.tarifasSiCompleto(json.readTree(respuesta.body()), reloj.ahora());
      if (tarifas.isPresent()) {
        // Completo: aqui si sabemos que no hay cobertura, y es lo unico que lo sabe.
        return tarifas.get().isEmpty()
            ? new ResultadoCotizacion.SinCobertura()
            : new ResultadoCotizacion.ConTarifas(tarifas.get());
      }
    }
    return sondeoAgotado(idCotizacion, "se agotaron los " + intentosDeSondeo + " intentos");
  }

  /**
   * La cotizacion <strong>sigue viva del otro lado</strong>: no completo dentro de nuestra ventana,
   * que es una decision nuestra para no dejar al comprador mirando una pantalla quieta. Por eso se
   * distingue del proveedor caido: al reintentar, la deduplicacion por contenido de Skydropx
   * devuelve esta misma cotizacion, ya completa, en un par de segundos (docs/13 6.9).
   */
  private ResultadoCotizacion sondeoAgotado(String idCotizacion, String porque) {
    return fallo(
        ResultadoCotizacion.Motivo.SONDEO_AGOTADO,
        "la cotizacion " + idCotizacion + " no completo: " + porque);
  }

  /**
   * El rastreo de una guía. Falla cerrado igual que la cotización, y por el mismo motivo de fondo:
   * quien llama es la tarea de conciliación, y un proveedor caído no puede tumbar el lote entero
   * (adr/0022). Lista vacía significa "no sé nada nuevo".
   *
   * <p><strong>Un 404 es una respuesta normal, no un fallo.</strong> Medido el 16 de septiembre de
   * 2026 sobre las cuatro guías emitidas: las tres que nunca se movieron responden {@code 404 "No
   * se encontró eventos de rastreo para ese número de guía"}. Una guía recién emitida está
   * exactamente en ese caso, así que esto va a ser lo habitual y no lo excepcional.
   *
   * <p><strong>El código de la transportadora es obligatorio y es el de la plataforma</strong>
   * —{@code servientrega}, {@code ninetynineminutes}—, no el nombre que se le muestra a nadie. Con
   * el nombre visible la respuesta es 404, igual que sin el parámetro, así que una guía de la que
   * no conocemos el código no se puede consultar: lo filtra {@code ConciliarEnvios} antes de llamar
   * aquí.
   */
  @Override
  public List<AplicarEventoDeEnvioComando> consultar(String codigoTransportadora, String guia) {
    Objects.requireNonNull(
        codigoTransportadora, "El código de la transportadora no puede ser nulo.");
    Objects.requireNonNull(guia, "La guía no puede ser nula.");
    try {
      return consultarOFallarCerrado(codigoTransportadora, guia);
    } catch (IOException | RuntimeException e) {
      return List.of();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return List.of();
    }
  }

  private List<AplicarEventoDeEnvioComando> consultarOFallarCerrado(
      String codigoTransportadora, String guia) throws IOException, InterruptedException {
    String token = token();
    if (token == null) {
      return List.of();
    }

    // Con parámetros de consulta y no en la ruta: la forma de la ruta —que anotaba adr/0022—
    // responde 404 Not Found. Comprobadas las dos el 16 de septiembre de 2026.
    String ruta =
        "/api/v1/shipments/tracking?tracking_number="
            + URLEncoder.encode(guia, StandardCharsets.UTF_8)
            + "&carrier_name="
            + URLEncoder.encode(codigoTransportadora, StandardCharsets.UTF_8);
    HttpResponse<String> respuesta = enviar(peticion(ruta, token).GET().build());
    if (respuesta.statusCode() / 100 != 2) {
      return List.of();
    }
    return mapeadorSeguimiento.eventos(json.readTree(respuesta.body()), guia);
  }

  /**
   * Pide las guías. <strong>Que responda {@code Aceptada} significa que la plataforma
   * cobró</strong>, no que haya guía: el {@code 202} llega con {@code payment_status: paid} y
   * {@code master_tracking_number: null}.
   *
   * <p><strong>Aquí sí se reintenta, y es lo contrario de lo que parece prudente.</strong> Un
   * {@code 408} de Skydropx no significa que no pasó nada: la primera emisión del proyecto
   * respondió "tiempo de espera excedido" con la guía ya creada y 19.465 descontados del saldo
   * (docs/13 §6.2). Con {@code unique_shipment: true} en el cuerpo, repetir la llamada con la misma
   * tarifa devuelve <em>esos mismos</em> envíos —la plataforma los cachea 96 horas por {@code
   * rate_id}—, así que insistir es cómo se recuperan los identificadores de una guía que ya se
   * pagó. Rendirse al primer corte es lo que la perdería.
   *
   * <p>Y por eso, cuando se agotan los intentos, el detalle lo dice con todas sus letras: puede
   * haber una guía viva del otro lado. No es una hipótesis — es el caso medido.
   */
  @Override
  public ResultadoEmision emitir(SolicitudDeEmision solicitud) {
    Objects.requireNonNull(solicitud, "La solicitud de emisión no puede ser nula.");
    try {
      return emitirConReintentos(solicitud);
    } catch (IOException | RuntimeException e) {
      return rechazo(
          ResultadoEmision.Motivo.PROVEEDOR_NO_DISPONIBLE, quizaQuedoCreada(e.toString()));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return rechazo(
          ResultadoEmision.Motivo.PROVEEDOR_NO_DISPONIBLE, quizaQuedoCreada("hilo interrumpido"));
    }
  }

  private ResultadoEmision emitirConReintentos(SolicitudDeEmision solicitud)
      throws IOException, InterruptedException {
    String token = token();
    if (token == null) {
      return rechazo(
          ResultadoEmision.Motivo.SIN_CREDENCIALES,
          "no se obtuvo token; revisar SKYDROPX_CLIENT_ID y SKYDROPX_CLIENT_SECRET");
    }

    String cuerpo = mapeadorEmision.cuerpoDeEmision(solicitud, origen);
    IOException ultimoCorte = null;
    for (int intento = 0; intento < INTENTOS_DE_EMISION; intento++) {
      if (intento > 0) {
        pausador.pausar(ESPERA_ENTRE_EMISIONES);
      }
      HttpResponse<String> respuesta;
      try {
        respuesta =
            enviar(
                peticion("/api/v2/shipments", token)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(cuerpo, StandardCharsets.UTF_8))
                    .build());
      } catch (IOException corte) {
        // La peticion se cayo, y el envio pudo haberse creado igual. `unique_shipment` hace que
        // volver a preguntar con la misma tarifa sea recuperar, no duplicar.
        ultimoCorte = corte;
        continue;
      }

      int estado = respuesta.statusCode();
      // 408: la creacion siguio del otro lado. 409: `unique_shipment` avisa de que hay una en
      // proceso con esta misma tarifa. Los dos se resuelven insistiendo, nunca cambiando el cuerpo.
      if (estado == 408 || estado == 409) {
        ultimoCorte = null;
        continue;
      }
      if (estado / 100 == 5) {
        return rechazo(
            ResultadoEmision.Motivo.PROVEEDOR_NO_DISPONIBLE,
            "la creacion del envio respondio " + estado);
      }
      if (estado / 100 == 4) {
        // Los nombres de los campos que la plataforma rechazo, sin sus valores: cuando la tarifa no
        // resuelve, el 422 enumera los campos que el envio habria heredado de la cotizacion, y eso
        // es lo que hace falta para diagnosticar. Los valores no: son el telefono, el nombre y la
        // direccion del comprador, y docs/08-seguridad-legal.md dice que en el registro no van
        // datos personales. El camino de cotizacion ya lo cuidaba y este no lo hacia.
        return rechazo(
            ResultadoEmision.Motivo.DATOS_RECHAZADOS,
            estado + " campos rechazados: " + camposRechazados(respuesta.body()));
      }

      List<String> envios = mapeadorEmision.enviosCreados(json.readTree(respuesta.body()));
      if (envios.isEmpty()) {
        return rechazo(
            ResultadoEmision.Motivo.RESPUESTA_INESPERADA,
            "la plataforma acepto la emision y no devolvio ningun envio");
      }
      return new ResultadoEmision.Aceptada(envios);
    }
    return rechazo(
        ResultadoEmision.Motivo.PROVEEDOR_NO_DISPONIBLE,
        quizaQuedoCreada(
            ultimoCorte == null
                ? "se agotaron los " + INTENTOS_DE_EMISION + " intentos con 408/409"
                : ultimoCorte.toString()));
  }

  /**
   * Relee un envío ya aceptado. Falla cerrado como todo lo demás, pero con una diferencia que
   * importa: "no se pudo preguntar" es un caso propio del puerto y no se disfraza de "sigue en
   * curso". Concluir sobre algo que nadie contestó es lo que cerraría una emisión a ciegas.
   */
  @Override
  public LecturaDeEnvioEmitido consultar(String idEnvioEnPlataforma) {
    Objects.requireNonNull(idEnvioEnPlataforma, "El id del envío no puede ser nulo.");
    try {
      String token = token();
      if (token == null) {
        return new LecturaDeEnvioEmitido.NoSeSabe();
      }
      // Por v1: `GET /api/v2/shipments/{id}` no existe —404 con el HTML del sitio, medido el 16 de
      // septiembre de 2026—. Crear va por v2 y releer por v1, a proposito (docs/13 §6.10).
      HttpResponse<String> respuesta =
          enviar(
              peticion(
                      "/api/v1/shipments/"
                          + URLEncoder.encode(idEnvioEnPlataforma, StandardCharsets.UTF_8),
                      token)
                  .GET()
                  .build());
      if (respuesta.statusCode() / 100 != 2) {
        log.warn(
            "No se pudo releer el envio {}: respondio {}",
            idEnvioEnPlataforma,
            respuesta.statusCode());
        return new LecturaDeEnvioEmitido.NoSeSabe();
      }
      return mapeadorEmision.lectura(json.readTree(respuesta.body()));
    } catch (IOException | RuntimeException e) {
      log.warn("No se pudo releer el envio {}: {}", idEnvioEnPlataforma, e.toString());
      return new LecturaDeEnvioEmitido.NoSeSabe();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return new LecturaDeEnvioEmitido.NoSeSabe();
    }
  }

  /**
   * El rechazo de una emision, en un solo sitio. Va en {@code error} y no en {@code warn} —al reves
   * que el de la cotizacion—: una cotizacion que falla deja al comprador con la recogida en el
   * punto, pero un despacho que no sale deja un pedido pagado sin mover, y eso lo mira alguien hoy.
   */
  private ResultadoEmision rechazo(ResultadoEmision.Motivo motivo, String detalle) {
    log.error("No se pudo emitir la guia ({}): {}", motivo, detalle);
    return new ResultadoEmision.Rechazada(motivo, detalle);
  }

  private static String quizaQuedoCreada(String causa) {
    return causa
        + " - el envio PUEDE haber quedado creado y cobrado: reintentar la emision con la misma"
        + " tarifa lo recupera por idempotencia durante 96 horas, y pasado ese plazo hay que"
        + " buscarlo en el panel de Skydropx antes de volver a emitir";
  }

  /**
   * Los nombres de los campos que vienen dentro de {@code errors}, y nada mas. Si la respuesta no
   * tiene esa forma se dice que no la tiene, en vez de volcar el cuerpo: un cuerpo inesperado es
   * justo donde puede venir cualquier cosa, incluido lo que mandamos.
   */
  private String camposRechazados(String cuerpo) {
    if (cuerpo == null || cuerpo.isBlank()) {
      return "(respuesta vacia)";
    }
    try {
      JsonNode errores = json.readTree(cuerpo).path("errors");
      if (errores.isObject()) {
        List<String> nombres = new ArrayList<>();
        errores.propertyNames().forEach(nombres::add);
        if (!nombres.isEmpty()) {
          return String.join(", ", nombres);
        }
      }
    } catch (RuntimeException e) {
      // Cae al mensaje generico de abajo.
    }
    return "(la respuesta no trae `errors`; no se vuelca por si lleva datos del comprador)";
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
