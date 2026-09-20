package co.tecnosport.api.infrastructure.pago;

import co.tecnosport.api.application.pago.PasarelaSistecredito;
import co.tecnosport.api.application.pago.SistecreditoNoRespondeException;
import co.tecnosport.api.application.pago.SolicitudTransaccionSistecredito;
import co.tecnosport.api.application.pago.TransaccionSistecredito;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Cliente de la pasarela de Sistecrédito ({@code adr/0048}, guías {@code G-ALI-08}, {@code
 * G-ALI-10} y {@code G-ALI-12}).
 *
 * <p>Dos endpoints: {@code POST /pay/create} y {@code GET /pay/GetTransactionResponse}. Las seis
 * cabeceras son las que documenta {@code G-ALI-10}; el ambiente lo decide {@code SCOrigen} junto
 * con la llave de suscripción, y aquí llega desde configuración porque esta cuenta <b>solo tiene
 * credenciales productivas</b> y el valor no puede quedar escrito en el código.
 *
 * <p><b>Un rechazo no es una caída.</b> La pasarela responde 200 con {@code errorCode: 0} y aun así
 * el medio de pago puede haber rechazado la transacción dentro de {@code paymentMethodResponse} —el
 * {@code 802}, "el valor solicitado es menor al mínimo", llega exactamente así—. Por eso lo que
 * vuelve de {@link #crear} es siempre una {@link TransaccionSistecredito} con su estado y su
 * descripción, y {@link SistecreditoNoRespondeException} queda solo para lo que de verdad es una
 * caída: sin respuesta, o con una respuesta ilegible.
 */
public final class SistecreditoClient implements PasarelaSistecredito {

  private static final Logger log = LoggerFactory.getLogger(SistecreditoClient.class);

  /** Sin coordenadas del comprador: la guía admite {@code 0,0} y no las necesitamos para nada. */
  private static final String SIN_COORDENADAS = "0,0";

  private static final String PAIS = "co";
  private static final String MONEDA = "COP";

  /** La pasarela manda la notificación por POST o por GET; la guía recomienda POST. */
  private static final String METODO_CONFIRMACION = "POST";

  private final URI urlBase;
  private final String llaveSuscripcion;
  private final String storeId;
  private final String vendorId;
  private final String ambiente;
  private final int metodoDePagoId;
  private final Duration timeout;
  private final int intentosDeSondeo;
  private final Duration esperaEntreSondeos;
  private final Pausador pausador;
  private final HttpClient httpClient = HttpClient.newHttpClient();
  private final JsonMapper json = JsonMapper.builder().build();

  /**
   * Extraída para que las pruebas observen cuánto se habría dormido en vez de dormirlo — mismo
   * motivo y misma forma que la de {@code LimitadorDePeticiones}, que vive en otro paquete.
   */
  interface Pausador {
    void pausar(Duration duracion) throws InterruptedException;
  }

  public SistecreditoClient(
      String urlBase,
      String llaveSuscripcion,
      String storeId,
      String vendorId,
      String ambiente,
      int metodoDePagoId,
      Duration timeout,
      int intentosDeSondeo,
      Duration esperaEntreSondeos) {
    this(
        URI.create(exigir(urlBase, "La URL base de Sistecrédito")),
        exigir(llaveSuscripcion, "La llave de suscripción de Sistecrédito"),
        exigir(storeId, "El storeId de Sistecrédito"),
        exigir(vendorId, "El vendorId de Sistecrédito"),
        exigir(ambiente, "El ambiente de Sistecrédito"),
        metodoDePagoId,
        timeout,
        intentosDeSondeo,
        esperaEntreSondeos,
        Thread::sleep);
  }

  /** Punto de extensión para pruebas: servidor local y pausas observables. */
  SistecreditoClient(
      URI urlBase,
      String llaveSuscripcion,
      String storeId,
      String vendorId,
      String ambiente,
      int metodoDePagoId,
      Duration timeout,
      int intentosDeSondeo,
      Duration esperaEntreSondeos,
      Pausador pausador) {
    this.urlBase = Objects.requireNonNull(urlBase, "La URL base no puede ser nula.");
    this.llaveSuscripcion = llaveSuscripcion;
    this.storeId = storeId;
    this.vendorId = vendorId;
    this.ambiente = ambiente;
    this.metodoDePagoId = metodoDePagoId;
    this.timeout = Objects.requireNonNull(timeout, "El timeout no puede ser nulo.");
    if (intentosDeSondeo <= 0) {
      throw new IllegalArgumentException(
          "Los intentos de sondeo deben ser mayores que cero: " + intentosDeSondeo);
    }
    this.intentosDeSondeo = intentosDeSondeo;
    this.esperaEntreSondeos =
        Objects.requireNonNull(esperaEntreSondeos, "La espera entre sondeos no puede ser nula.");
    this.pausador = Objects.requireNonNull(pausador, "El pausador no puede ser nulo.");
  }

  @Override
  public TransaccionSistecredito crear(SolicitudTransaccionSistecredito solicitud) {
    Objects.requireNonNull(solicitud, "La solicitud no puede ser nula.");
    String cuerpo = json.writeValueAsString(cuerpoDeCreacion(solicitud));
    HttpRequest peticion =
        conCabeceras(HttpRequest.newBuilder(URI.create(urlBase + "/create")))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(cuerpo, StandardCharsets.UTF_8))
            .build();

    HttpResponse<String> respuesta = enviar(peticion, "crear la transacción");
    JsonNode raiz = leer(respuesta.body());
    JsonNode datos = raiz.path("data");
    if (datos.isMissingNode() || datos.isNull() || !datos.hasNonNull("_id")) {
      // 400 con errorCode/message: la pasarela rechazó la petición antes de llegar al medio de
      // pago (data viene vacío, G-SCL-21 §3.3). No hay transacción que devolver.
      throw new SistecreditoNoRespondeException(
          "Sistecrédito rechazó la creación: HTTP "
              + respuesta.statusCode()
              + ", errorCode="
              + texto(raiz.path("errorCode"))
              + ", message="
              + texto(raiz.path("message")));
    }
    return sondearHastaLaUrl(aTransaccion(datos));
  }

  /**
   * La URL de pago casi nunca viene en la respuesta de creación: la pasarela todavía está hablando
   * con el medio de pago, y hay que consultar hasta que aparezca (guía {@code G-ALI-12}). El sondeo
   * vive aquí y no en el caso de uso por lo mismo que el de Skydropx: es una particularidad del
   * protocolo de este proveedor, y {@code application} no tiene por qué saber dormir un hilo.
   *
   * <p>Para en cuanto el estado es terminal, no solo cuando se acaban los intentos: una transacción
   * rechazada no va a producir una URL nunca, y seguir preguntando solo retrasa el mensaje que el
   * comprador tiene que ver.
   */
  private TransaccionSistecredito sondearHastaLaUrl(TransaccionSistecredito creada) {
    if (creada.tieneUrlDeRedireccion() || esTerminal(creada.estado())) {
      return creada;
    }
    TransaccionSistecredito ultima = creada;
    for (int intento = 0; intento < intentosDeSondeo; intento++) {
      try {
        pausador.pausar(esperaEntreSondeos);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return ultima;
      }
      Optional<TransaccionSistecredito> consultada = consultar(creada.id());
      if (consultada.isEmpty()) {
        continue;
      }
      ultima = consultada.get();
      if (ultima.tieneUrlDeRedireccion() || esTerminal(ultima.estado())) {
        return ultima;
      }
    }
    // Sin URL y sin estado terminal: la transacción existe y quedó viva. Se devuelve tal cual, con
    // su id, para que quien llame la guarde y la conciliación pueda resolverla después — perderla
    // aquí dejaría un intento de pago huérfano en la pasarela.
    log.warn(
        "Sistecrédito no entregó la URL de pago de la transacción {} tras {} sondeos; sigue en {}.",
        creada.id(),
        intentosDeSondeo,
        ultima.estado());
    return ultima;
  }

  /**
   * Los estados de los que ya no se sale (guía {@code G-ALI-08}, "Interpretación de la respuesta").
   * {@code Approved} no está: aprobado sin URL no es un caso del que haya que huir, y si llegara
   * tampoco hay nada más que sondear — lo atrapa el tope de intentos.
   */
  private static boolean esTerminal(String estado) {
    return switch (estado == null ? "" : estado.trim()) {
      case "Rejected", "Cancelled", "Expired", "Abandoned", "Failed" -> true;
      default -> false;
    };
  }

  @Override
  public Optional<TransaccionSistecredito> consultar(String idTransaccion) {
    if (idTransaccion == null || idTransaccion.isBlank()) {
      return Optional.empty();
    }
    URI destino =
        URI.create(
            urlBase
                + "/GetTransactionResponse?transactionId="
                + URLEncoder.encode(idTransaccion, StandardCharsets.UTF_8));
    try {
      HttpResponse<String> respuesta =
          enviar(conCabeceras(HttpRequest.newBuilder(destino)).GET().build(), "consultar");
      JsonNode datos = leer(respuesta.body()).path("data");
      if (datos.isMissingNode() || datos.isNull() || !datos.hasNonNull("_id")) {
        return Optional.empty();
      }
      return Optional.of(aTransaccion(datos));
    } catch (SistecreditoNoRespondeException e) {
      // Consultar es lo que hacen el sondeo y la conciliación, y los dos reintentan solos: una
      // caída aquí no es una excepción que deba subir, es un "todavía no sé".
      log.warn(
          "No se pudo consultar la transacción {} en Sistecrédito: {}",
          idTransaccion,
          e.getMessage());
      return Optional.empty();
    }
  }

  private ObjectNode cuerpoDeCreacion(SolicitudTransaccionSistecredito solicitud) {
    ObjectNode cuerpo = json.createObjectNode();
    cuerpo.put("invoice", solicitud.referencia().valor());
    cuerpo.put("description", solicitud.descripcion());
    // Solo paymentMethodId, que es lo que la guía de ESTE medio de pago manda en su ejemplo
    // (G-ALI-12 §"Consumo inicial"). La guía general lista además bankCode y userType; si la
    // pasarela llegara a exigirlos responderá con un 6xx que los nombra, y ahí se agregan con la
    // certeza de que hacían falta.
    cuerpo.putObject("paymentMethod").put("paymentMethodId", metodoDePagoId);
    cuerpo.put("currency", MONEDA);
    // "Valor factura de la compra sin decimales" (G-ALI-10). El peso no se fracciona en este
    // dominio, así que el entero es exacto y no hay redondeo que decidir aquí.
    cuerpo.put("value", solicitud.monto().valor().longValueExact());
    ObjectNode sandbox = cuerpo.putObject("sandbox");
    sandbox.put("isActive", solicitud.sandbox());
    if (solicitud.sandbox()) {
      sandbox.put("status", solicitud.estadoSimulado());
    }
    cuerpo.put("urlResponse", solicitud.urlRespuesta());
    cuerpo.put("urlConfirmation", solicitud.urlConfirmacion());
    cuerpo.put("methodConfirmation", METODO_CONFIRMACION);
    ObjectNode cliente = cuerpo.putObject("client");
    cliente.put("docType", solicitud.documento().tipo().name());
    cliente.put("document", solicitud.documento().numero());
    return cuerpo;
  }

  private TransaccionSistecredito aTransaccion(JsonNode datos) {
    JsonNode respuestaMedio = datos.path("paymentMethodResponse");
    return new TransaccionSistecredito(
        texto(datos.path("_id")),
        texto(datos.path("invoice")),
        texto(datos.path("transactionStatus")),
        urlDeRedireccion(datos, respuestaMedio),
        texto(respuestaMedio.path("codeResponse")),
        texto(respuestaMedio.path("description")));
  }

  /**
   * Las dos guías no coinciden sobre dónde vive esta propiedad: {@code G-ALI-12} dice textualmente
   * que está "en el nodo paymentMethodResponse", y la tabla de {@code G-SCL-21} §3.3 la lista al
   * nivel de {@code data}. Se miran los dos sitios en ese orden en vez de elegir uno: equivocarse
   * aquí no da un error, da un sondeo que nunca termina.
   */
  private String urlDeRedireccion(JsonNode datos, JsonNode respuestaMedio) {
    String enElMedio = texto(respuestaMedio.path("paymentRedirectUrl"));
    return enElMedio != null ? enElMedio : texto(datos.path("paymentRedirectUrl"));
  }

  /**
   * Nulo en vez de cadena vacía cuando el campo no viene: quien recibe la transacción distingue "la
   * pasarela no dijo nada" de "dijo una cadena vacía", y de eso depende que el sondeo siga o pare.
   */
  private static String texto(JsonNode nodo) {
    if (nodo == null || nodo.isMissingNode() || nodo.isNull()) {
      return null;
    }
    String valor = nodo.asString();
    if (valor == null || valor.isBlank()) {
      return null;
    }
    return valor.trim();
  }

  private HttpRequest.Builder conCabeceras(HttpRequest.Builder builder) {
    return builder
        .timeout(timeout)
        .header("SCLocation", SIN_COORDENADAS)
        .header("SCOrigen", ambiente)
        .header("country", PAIS)
        .header("Ocp-Apim-Subscription-Key", llaveSuscripcion)
        .header("ApplicationKey", storeId)
        .header("ApplicationToken", vendorId);
  }

  private HttpResponse<String> enviar(HttpRequest peticion, String queSeIntentaba) {
    try {
      return httpClient.send(peticion, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    } catch (IOException e) {
      throw new SistecreditoNoRespondeException(
          "Sistecrédito no respondió al " + queSeIntentaba + ".", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new SistecreditoNoRespondeException(
          "Se interrumpió la llamada a Sistecrédito al " + queSeIntentaba + ".", e);
    }
  }

  private JsonNode leer(String cuerpo) {
    try {
      return json.readTree(cuerpo);
    } catch (JacksonException e) {
      throw new SistecreditoNoRespondeException("Sistecrédito respondió algo que no es JSON.", e);
    }
  }

  private static String exigir(String valor, String queEs) {
    if (valor == null || valor.isBlank()) {
      throw new IllegalArgumentException(queEs + " no puede estar vacía.");
    }
    return valor;
  }
}
