package co.tecnosport.api.infrastructure.pago;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.application.pago.SistecreditoNoRespondeException;
import co.tecnosport.api.application.pago.SolicitudTransaccionSistecredito;
import co.tecnosport.api.application.pago.TransaccionSistecredito;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.DocumentoIdentidad;
import co.tecnosport.api.domain.compartido.TipoDocumento;
import co.tecnosport.api.domain.pago.ReferenciaPago;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Contra un servidor HTTP del JDK ({@link HttpServer}, sin dependencia nueva) en vez de
 * api.credinet.co: esta integración solo tiene credenciales productivas, así que una prueba que
 * llamara de verdad pediría un crédito a nombre de una persona.
 *
 * <p>Los cuerpos de respuesta son los de los ejemplos de las guías {@code G-ALI-12} y {@code
 * G-SCL-21}, recortados a los campos que el cliente lee.
 */
class SistecreditoClientTest {

  private static final ReferenciaPago REFERENCIA = new ReferenciaPago("TS-2026-000001-1");
  private static final DocumentoIdentidad DOCUMENTO =
      new DocumentoIdentidad(TipoDocumento.CC, "1017254896");

  private final JsonMapper json = JsonMapper.builder().build();
  private HttpServer servidor;

  @AfterEach
  void apagarServidor() {
    if (servidor != null) {
      servidor.stop(0);
    }
  }

  @Test
  void mandaLasSeisCabecerasQueLaGuiaExige() throws IOException {
    AtomicReference<Map<String, String>> cabeceras = new AtomicReference<>();
    URI base = servirCreacion(respuestaDeCreacion(null), cabeceras, new AtomicReference<>());

    cliente(base).crear(solicitud(false, null));

    Map<String, String> recibidas = cabeceras.get();
    assertEquals("0,0", recibidas.get("Sclocation"));
    assertEquals("Production", recibidas.get("Scorigen"));
    assertEquals("co", recibidas.get("Country"));
    assertEquals("llave-de-suscripcion", recibidas.get("Ocp-apim-subscription-key"));
    assertEquals("el-store-id", recibidas.get("Applicationkey"));
    assertEquals("el-vendor-id", recibidas.get("Applicationtoken"));
  }

  @Test
  void elCuerpoLlevaLaReferenciaComoFacturaYElMontoSinDecimales() throws IOException {
    AtomicReference<String> cuerpo = new AtomicReference<>();
    URI base = servirCreacion(respuestaDeCreacion(null), new AtomicReference<>(), cuerpo);

    cliente(base).crear(solicitud(false, null));

    JsonNode enviado = json.readTree(cuerpo.get());
    assertEquals("TS-2026-000001-1", enviado.path("invoice").asString());
    assertEquals("COP", enviado.path("currency").asString());
    assertEquals(150000, enviado.path("value").asInt());
    assertEquals(2, enviado.path("paymentMethod").path("paymentMethodId").asInt());
    assertEquals("POST", enviado.path("methodConfirmation").asString());
    assertEquals("CC", enviado.path("client").path("docType").asString());
    assertEquals("1017254896", enviado.path("client").path("document").asString());
  }

  /**
   * El freno de seguridad de {@code adr/0048}. Apagado, el nodo {@code sandbox} va con {@code
   * isActive: false} y sin estado simulado: nada que la pasarela pueda tomar por una simulación.
   */
  @Test
  void sinModoSandboxNoViajaNingunEstadoSimulado() throws IOException {
    AtomicReference<String> cuerpo = new AtomicReference<>();
    URI base = servirCreacion(respuestaDeCreacion(null), new AtomicReference<>(), cuerpo);

    cliente(base).crear(solicitud(false, null));

    JsonNode sandbox = json.readTree(cuerpo.get()).path("sandbox");
    assertFalse(sandbox.path("isActive").asBoolean());
    assertTrue(sandbox.path("status").isMissingNode());
  }

  @Test
  void conModoSandboxViajaElEstadoQueSeQuiereSimular() throws IOException {
    AtomicReference<String> cuerpo = new AtomicReference<>();
    URI base = servirCreacion(respuestaDeCreacion(null), new AtomicReference<>(), cuerpo);

    cliente(base).crear(solicitud(true, "Approved"));

    JsonNode sandbox = json.readTree(cuerpo.get()).path("sandbox");
    assertTrue(sandbox.path("isActive").asBoolean());
    assertEquals("Approved", sandbox.path("status").asString());
  }

  /** La creación responde con estado y sin URL: todavía no habló con el medio de pago. */
  @Test
  void laCreacionDevuelveElIdYElEstadoAunqueNoHayaUrlTodavia() throws IOException {
    URI base =
        servirCreacion(respuestaDeCreacion(null), new AtomicReference<>(), new AtomicReference<>());

    TransaccionSistecredito transaccion = cliente(base).crear(solicitud(false, null));

    assertEquals("649b4c821b581f96e45b5696", transaccion.id());
    assertEquals("PendingForPaymentMethod", transaccion.estado());
    assertEquals("TS-2026-000001-1", transaccion.referencia());
    assertFalse(transaccion.tieneUrlDeRedireccion());
    assertNull(transaccion.urlRedireccion());
  }

  /**
   * {@code G-ALI-12} dice que la URL vive dentro de {@code paymentMethodResponse}; la tabla de
   * {@code G-SCL-21} la lista al nivel de {@code data}. El cliente mira los dos sitios porque
   * equivocarse ahí no da un error: da un sondeo que no termina nunca.
   */
  @Test
  void encuentraLaUrlDeRedireccionEnCualquieraDeLosDosSitiosQueLasGuiasDicen() throws IOException {
    URI enElMedio = servirConsulta(respuestaConsultada("https://siste.credinet.co/pago/abc", null));
    assertEquals(
        Optional.of("https://siste.credinet.co/pago/abc"),
        cliente(enElMedio).consultar("649b4c821b581f96e45b5696").orElseThrow().urlDeRedireccion());

    apagarServidor();
    URI enLaRaiz = servirConsulta(respuestaConsultada(null, "https://siste.credinet.co/pago/xyz"));
    assertEquals(
        Optional.of("https://siste.credinet.co/pago/xyz"),
        cliente(enLaRaiz).consultar("649b4c821b581f96e45b5696").orElseThrow().urlDeRedireccion());
  }

  /**
   * El {@code 802} llega con HTTP 200 y {@code errorCode: 0}: mirar solo el código HTTP lo daría
   * por bueno. Lo que lo delata es el nodo del medio de pago, y por eso el cliente lo devuelve
   * entero en vez de quedarse con el estado.
   */
  @Test
  void unRechazoDelMedioDePagoLlegaCon200YSeDevuelveConSuCodigo() throws IOException {
    String cuerpo =
        """
        {"function":"/checkout/create/transaction","errorCode":0,"message":"Petición realizada con éxito",
         "country":"co","data":{"_id":"649b4c821b581f96e45b5696","invoice":"TS-2026-000001-1",
         "transactionStatus":"Rejected","paymentMethodResponse":{"codeResponse":"802",
         "description":"El valor del crédito solicitado es menor al valor mínimo"}}}
        """;
    URI base = servirCreacion(cuerpo, new AtomicReference<>(), new AtomicReference<>());

    TransaccionSistecredito transaccion = cliente(base).crear(solicitud(false, null));

    assertEquals("Rejected", transaccion.estado());
    assertEquals("802", transaccion.codigoMedioDePago());
    assertTrue(transaccion.descripcion().contains("menor al valor mínimo"));
  }

  /** Un 400 sin {@code data} sí es una caída: no hay transacción de la que informar. */
  @Test
  void unRechazoDeLaPasarelaSinDatosEsUnaCaida() throws IOException {
    URI base =
        servirCreacion(
            "{\"errorCode\":738,\"message\":\"738 - Ya existe una transacción activa\",\"data\":null}",
            new AtomicReference<>(),
            new AtomicReference<>());

    SistecreditoNoRespondeException error =
        assertThrows(
            SistecreditoNoRespondeException.class,
            () -> cliente(base).crear(solicitud(false, null)));
    assertTrue(error.getMessage().contains("738"));
  }

  /**
   * Consultar es lo que hacen el sondeo y la conciliación, que reintentan solos: una caída ahí es
   * un "todavía no sé", no una excepción que deba tumbar a quien pregunta.
   */
  @Test
  void unaConsultaQueFallaDevuelveVacioEnVezDeReventar() throws IOException {
    URI base = servirConsulta("no soy json");

    assertEquals(Optional.empty(), cliente(base).consultar("649b4c821b581f96e45b5696"));
  }

  @Test
  void consultarSinIdNoLlamaANadie() {
    assertEquals(Optional.empty(), cliente(URI.create("http://localhost:1")).consultar(null));
    assertEquals(Optional.empty(), cliente(URI.create("http://localhost:1")).consultar("  "));
  }

  private SistecreditoClient cliente(URI base) {
    return new SistecreditoClient(
        base,
        "llave-de-suscripcion",
        "el-store-id",
        "el-vendor-id",
        "Production",
        2,
        Duration.ofSeconds(5));
  }

  private SolicitudTransaccionSistecredito solicitud(boolean sandbox, String estadoSimulado) {
    return new SolicitudTransaccionSistecredito(
        REFERENCIA,
        "Pedido TS-2026-000001",
        Dinero.deCop(150_000),
        DOCUMENTO,
        "https://tecnosport.co/checkout/retorno",
        "https://api.tecnosport.co/api/v1/pagos/sistecredito/confirmacion",
        sandbox,
        estadoSimulado);
  }

  private static String respuestaDeCreacion(String urlRedireccion) {
    String nodoUrl =
        urlRedireccion == null ? "" : ",\"paymentRedirectUrl\":\"" + urlRedireccion + "\"";
    return """
        {"function":"/checkout/create/transaction","errorCode":0,"message":"Petición realizada con éxito",
         "country":"co","data":{"_id":"649b4c821b581f96e45b5696","invoice":"TS-2026-000001-1",
         "transactionStatus":"PendingForPaymentMethod",
         "paymentMethodResponse":{"statusResponse":"PendingForPaymentMethod","codeResponse":"3"%s}}}
        """
        .formatted(nodoUrl);
  }

  private static String respuestaConsultada(String urlEnElMedio, String urlEnLaRaiz) {
    return """
        {"errorCode":0,"data":{"_id":"649b4c821b581f96e45b5696","invoice":"TS-2026-000001-1",
         "transactionStatus":"Pending"%s,
         "paymentMethodResponse":{"statusResponse":"Pending","codeResponse":"1"%s}}}
        """
        .formatted(
            urlEnLaRaiz == null ? "" : ",\"paymentRedirectUrl\":\"" + urlEnLaRaiz + "\"",
            urlEnElMedio == null ? "" : ",\"paymentRedirectUrl\":\"" + urlEnElMedio + "\"");
  }

  private URI servirCreacion(
      String respuesta,
      AtomicReference<Map<String, String>> cabeceras,
      AtomicReference<String> cuerpo)
      throws IOException {
    return servir(
        "/pay/create",
        intercambio -> {
          cabeceras.set(
              intercambio.getRequestHeaders().entrySet().stream()
                  .collect(
                      java.util.stream.Collectors.toMap(
                          Map.Entry::getKey, entrada -> entrada.getValue().get(0))));
          cuerpo.set(
              new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          return respuesta;
        });
  }

  private URI servirConsulta(String respuesta) throws IOException {
    return servir("/pay/GetTransactionResponse", intercambio -> respuesta);
  }

  private URI servir(String ruta, Respondedor respondedor) throws IOException {
    servidor = HttpServer.create(new InetSocketAddress(0), 0);
    servidor.createContext(
        ruta,
        intercambio -> {
          String respuesta = respondedor.responder(intercambio);
          byte[] bytes = respuesta.getBytes(StandardCharsets.UTF_8);
          intercambio.getResponseHeaders().add("Content-Type", "application/json");
          intercambio.sendResponseHeaders(200, bytes.length);
          intercambio.getResponseBody().write(bytes);
          intercambio.close();
        });
    servidor.start();
    return URI.create("http://localhost:" + servidor.getAddress().getPort() + "/pay");
  }

  @FunctionalInterface
  private interface Respondedor {
    String responder(com.sun.net.httpserver.HttpExchange intercambio) throws IOException;
  }
}
