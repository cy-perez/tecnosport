package co.tecnosport.api.infrastructure.envio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.envio.AplicarEventoDeEnvioComando;
import co.tecnosport.api.application.envio.Bulto;
import co.tecnosport.api.application.envio.CotizacionEnvio;
import co.tecnosport.api.application.envio.ResultadoCotizacion;
import co.tecnosport.api.domain.catalogo.Paquete;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.envio.EstadoEnvio;
import co.tecnosport.api.domain.envio.TarifaEnvio;
import co.tecnosport.api.domain.pedido.Direccion;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * De extremo a extremo contra un servidor HTTP de prueba ({@link HttpServer}, del JDK, sin
 * dependencia nueva), igual que {@code WompiClientTest}.
 *
 * <p>Lo que se prueba aquí es el <strong>protocolo</strong>: el token se pide una vez y se
 * reutiliza, se renueva al vencer, el sondeo se corta por intentos y por tiempo, y cualquier fallo
 * termina en lista vacía. El mapeo entra por constructor y aquí se usa un doble.
 *
 * <p>Ese doble <strong>no valida</strong> que Skydropx use esos nombres de campo. Sería un
 * autoengaño: un servidor falso que habla el mismo idioma que el cliente pasa siempre. Lo único que
 * demuestra es que la máquina de alrededor funciona. El mapeo de verdad se prueba contra respuestas
 * capturadas de la cuenta real, en {@code MapeadorCotizacionSkydropxV1Test}.
 */
class SkydropxClientTest {

  private static final Instant AHORA = Instant.parse("2026-09-11T12:00:00Z");
  private static final Duration TOPE = Duration.ofSeconds(10);

  private static final OrigenDespacho ORIGEN =
      new OrigenDespacho(
          "TecnoSport",
          "+573138816711",
          "Cra. 26C # 38B-31",
          "Antioquia",
          "Medellín",
          "05001",
          null,
          "La Milagrosa",
          "Apartamento 401",
          "contacto@tecnosport.co");

  private static final CotizacionEnvio COTIZACION =
      new CotizacionEnvio(
          Direccion.sinBarrio("11", "Bogotá D.C.", "11001", "Bogotá", "Cra. 7 #12-34", null),
          List.of(new Bulto(new Paquete(180, 30, 25, 4), Dinero.deCop(150_000))));

  private HttpServer servidor;

  @AfterEach
  void detenerServidor() {
    if (servidor != null) {
      servidor.stop(0);
    }
  }

  // --- dobles de prueba -------------------------------------------------------------------

  private static final class RelojAjustable implements Reloj {
    private Instant ahora = AHORA;

    @Override
    public Instant ahora() {
      return ahora;
    }

    void avanzar(Duration duracion) {
      ahora = ahora.plus(duracion);
    }
  }

  /** Habla la forma que la documentación describe. No prueba que sea la real. */
  private static final class MapeadorDePrueba implements MapeadorCotizacionSkydropx {
    @Override
    public String cuerpoDeCotizacion(CotizacionEnvio cotizacion, OrigenDespacho origen) {
      return "{\"cotizacion\":\"de-prueba\"}";
    }

    @Override
    public Optional<String> idDeCotizacion(JsonNode respuesta) {
      String id = respuesta.path("id").asString();
      return id == null || id.isBlank() ? Optional.empty() : Optional.of(id);
    }

    @Override
    public Optional<List<TarifaEnvio>> tarifasSiCompleto(JsonNode respuesta, Instant ahora) {
      if (!respuesta.path("is_completed").asBoolean(false)) {
        return Optional.empty();
      }
      List<TarifaEnvio> tarifas = new ArrayList<>();
      for (JsonNode rate : respuesta.path("rates")) {
        tarifas.add(
            new TarifaEnvio(
                rate.path("id").asString(),
                rate.path("provider").asString(),
                rate.path("service_level").asString(),
                Dinero.deCop(rate.path("total_pricing").asLong()),
                rate.path("days").asInt(),
                rate.path("cash_on_delivery").asBoolean(false),
                ahora.plus(Duration.ofHours(24))));
      }
      return Optional.of(List.copyOf(tarifas));
    }
  }

  /**
   * Habla un idioma inventado a propósito: {@code {"eventos":[...]}}. Si aquí se usara el mapeador
   * real, esta prueba diría que el cliente entiende a Skydropx cuando lo único que demuestra es que
   * el servidor de prueba y el cliente se entienden entre ellos. El mapeo real se prueba contra la
   * respuesta capturada, en {@link MapeadorSeguimientoSkydropxV1Test}.
   */
  private static final class MapeadorDeSeguimientoDePrueba implements MapeadorSeguimientoSkydropx {
    @Override
    public List<AplicarEventoDeEnvioComando> eventos(JsonNode respuesta, String guia) {
      List<AplicarEventoDeEnvioComando> eventos = new ArrayList<>();
      for (JsonNode nodo : respuesta.path("eventos")) {
        eventos.add(
            new AplicarEventoDeEnvioComando(
                guia,
                EstadoEnvio.EN_TRANSITO,
                nodo.path("texto").asString(),
                AHORA,
                nodo.path("id").asString(),
                "skydropx"));
      }
      return List.copyOf(eventos);
    }
  }

  private final AtomicInteger peticionesDeToken = new AtomicInteger();
  private final AtomicInteger sondeos = new AtomicInteger();
  private final List<Duration> pausas = new ArrayList<>();

  // --- montaje ----------------------------------------------------------------------------

  private SkydropxClient clienteContra(
      String cuerpoToken, int estadoCreacion, String cuerpoCreacion, RespuestaDeSondeo sondeo)
      throws IOException {
    return clienteContra(
        cuerpoToken, estadoCreacion, cuerpoCreacion, sondeo, new MapeadorDePrueba(), 8);
  }

  private SkydropxClient clienteContra(
      String cuerpoToken,
      int estadoCreacion,
      String cuerpoCreacion,
      RespuestaDeSondeo sondeo,
      MapeadorCotizacionSkydropx mapeador,
      int intentos)
      throws IOException {
    servidor = HttpServer.create(new InetSocketAddress(0), 0);
    servidor.createContext(
        "/api/v1/oauth/token",
        intercambio -> {
          peticionesDeToken.incrementAndGet();
          responder(intercambio, 200, cuerpoToken);
        });
    servidor.createContext(
        "/api/v1/quotations",
        intercambio -> {
          if ("POST".equals(intercambio.getRequestMethod())) {
            responder(intercambio, estadoCreacion, cuerpoCreacion);
          } else {
            responder(intercambio, 200, sondeo.cuerpo(sondeos.incrementAndGet()));
          }
        });
    servidor.start();

    return new SkydropxClient(
        URI.create("http://localhost:" + servidor.getAddress().getPort()),
        "id-de-prueba",
        "secreto-de-prueba",
        ORIGEN,
        TOPE,
        intentos,
        Duration.ofMillis(500),
        reloj,
        mapeador,
        new MapeadorDeSeguimientoDePrueba(),
        new MapeadorEmisionSkydropxV2(),
        new LimitadorDePeticiones(Duration.ZERO, System::nanoTime, pausas::add),
        pausas::add,
        HttpClient.newHttpClient());
  }

  private interface RespuestaDeSondeo {
    String cuerpo(int numeroDeSondeo);
  }

  private final RelojAjustable reloj = new RelojAjustable();

  private static void responder(HttpExchange intercambio, int estado, String cuerpo)
      throws IOException {
    byte[] bytes = cuerpo.getBytes(StandardCharsets.UTF_8);
    intercambio.getResponseHeaders().add("Content-Type", "application/json");
    intercambio.sendResponseHeaders(estado, bytes.length);
    intercambio.getResponseBody().write(bytes);
    intercambio.close();
  }

  private static String token(long viveSegundos) {
    return "{\"access_token\":\"tok-123\",\"expires_in\":" + viveSegundos + "}";
  }

  private static final String UNA_TARIFA =
      """
      {"is_completed":true,"rates":[
        {"id":"r1","provider":"Servientrega","service_level":"Estándar",
         "total_pricing":15000,"days":3,"cash_on_delivery":true}]}
      """;

  // --- pruebas ----------------------------------------------------------------------------

  @Test
  void cotizaYDevuelveLasTarifasCuandoLaCotizacionCompleta() throws IOException {
    SkydropxClient cliente =
        clienteContra(token(7200), 200, "{\"id\":\"q1\"}", numero -> UNA_TARIFA);

    List<TarifaEnvio> tarifas = tarifasDe(cliente.cotizar(COTIZACION));

    assertEquals(1, tarifas.size());
    TarifaEnvio tarifa = tarifas.get(0);
    assertEquals("r1", tarifa.idTarifa());
    assertEquals("Servientrega", tarifa.transportadora());
    assertEquals(Dinero.deCop(15_000), tarifa.costo());
    assertEquals(3, tarifa.diasEstimados());
    assertTrue(tarifa.admiteContraentrega());
  }

  /** La prueba que pedía el plan de la fase: la cotización que nunca termina. */
  @Test
  void laCotizacionQueNuncaCompletaSeCortaPorIntentosYDevuelveVacio() throws IOException {
    SkydropxClient cliente =
        clienteContra(
            token(7200),
            200,
            "{\"id\":\"q1\"}",
            numero -> "{\"is_completed\":false,\"rates\":[]}",
            new MapeadorDePrueba(),
            4);

    assertEquals(ResultadoCotizacion.Motivo.SONDEO_AGOTADO, motivoDe(cliente.cotizar(COTIZACION)));
    assertEquals(4, sondeos.get());
  }

  /**
   * El otro tope, el de tiempo. Con intentos de sobra pero el reloj pasado del límite, el sondeo
   * corta igual: un proveedor lento no deja al comprador mirando una pantalla quieta.
   */
  @Test
  void elSondeoSeCortaPorTiempoAunqueQuedenIntentos() throws IOException {
    SkydropxClient cliente =
        clienteContra(
            token(7200),
            200,
            "{\"id\":\"q1\"}",
            numero -> {
              reloj.avanzar(Duration.ofSeconds(30));
              return "{\"is_completed\":false,\"rates\":[]}";
            },
            new MapeadorDePrueba(),
            100);

    assertEquals(ResultadoCotizacion.Motivo.SONDEO_AGOTADO, motivoDe(cliente.cotizar(COTIZACION)));
    assertEquals(1, sondeos.get());
  }

  @Test
  void unaCotizacionQueCompletaSinTarifasDevuelveVacioSinReintentar() throws IOException {
    SkydropxClient cliente =
        clienteContra(
            token(7200), 200, "{\"id\":\"q1\"}", numero -> "{\"is_completed\":true,\"rates\":[]}");

    assertInstanceOf(ResultadoCotizacion.SinCobertura.class, cliente.cotizar(COTIZACION));
    assertEquals(1, sondeos.get());
  }

  @Test
  void elTokenSePideUnaVezYSeReutilizaEntreCotizaciones() throws IOException {
    SkydropxClient cliente =
        clienteContra(token(7200), 200, "{\"id\":\"q1\"}", numero -> UNA_TARIFA);

    cliente.cotizar(COTIZACION);
    cliente.cotizar(COTIZACION);

    assertEquals(1, peticionesDeToken.get());
  }

  @Test
  void elTokenSeRenuevaCuandoEstaPorVencer() throws IOException {
    SkydropxClient cliente =
        clienteContra(token(7200), 200, "{\"id\":\"q1\"}", numero -> UNA_TARIFA);

    cliente.cotizar(COTIZACION);
    reloj.avanzar(Duration.ofMinutes(119));
    cliente.cotizar(COTIZACION);

    assertEquals(2, peticionesDeToken.get());
  }

  /**
   * Sin {@code expires_in} no se puede saber cuánto dura el token, así que no se cachea: se pide
   * uno por cotización. Suponerle una duración sería peor.
   */
  @Test
  void sinExpiresInElTokenNoSeCachea() throws IOException {
    SkydropxClient cliente =
        clienteContra(
            "{\"access_token\":\"tok-123\"}", 200, "{\"id\":\"q1\"}", numero -> UNA_TARIFA);

    cliente.cotizar(COTIZACION);
    cliente.cotizar(COTIZACION);

    assertEquals(2, peticionesDeToken.get());
  }

  @Test
  void unErrorAlCrearLaCotizacionDevuelveVacioSinSondear() throws IOException {
    SkydropxClient cliente = clienteContra(token(7200), 500, "{}", numero -> UNA_TARIFA);

    assertEquals(
        ResultadoCotizacion.Motivo.PROVEEDOR_NO_DISPONIBLE, motivoDe(cliente.cotizar(COTIZACION)));
    assertEquals(0, sondeos.get());
  }

  @Test
  void unaCreacionSinIdDevuelveVacioSinSondear() throws IOException {
    SkydropxClient cliente = clienteContra(token(7200), 200, "{}", numero -> UNA_TARIFA);

    assertEquals(
        ResultadoCotizacion.Motivo.RESPUESTA_INESPERADA, motivoDe(cliente.cotizar(COTIZACION)));
    assertEquals(0, sondeos.get());
  }

  /**
   * Un mapeador que revienta no puede tumbar el checkout: para el comprador tiene que ser
   * indistinguible de "el proveedor no respondió" — sin envío a domicilio, solo recogida en el
   * punto (adr/0021). Cubre el {@code catch (RuntimeException)} del cliente, que existe porque un
   * campo inesperado del proveedor no puede dejar a nadie sin comprar.
   */
  @Test
  void unMapeadorQueRevientaFallaCerradoYNoLanza() throws IOException {
    SkydropxClient cliente =
        clienteContra(
            token(7200),
            200,
            "{\"id\":\"q1\"}",
            numero -> UNA_TARIFA,
            new MapeadorQueRevienta(),
            8);

    assertEquals(
        ResultadoCotizacion.Motivo.PROVEEDOR_NO_DISPONIBLE, motivoDe(cliente.cotizar(COTIZACION)));
    assertEquals(0, sondeos.get());
  }

  /** Cualquier cosa inesperada al armar el cuerpo. */
  private static final class MapeadorQueRevienta implements MapeadorCotizacionSkydropx {

    @Override
    public String cuerpoDeCotizacion(CotizacionEnvio cotizacion, OrigenDespacho origen) {
      throw new IllegalStateException("Un campo que no estaba donde se esperaba.");
    }

    @Override
    public Optional<String> idDeCotizacion(JsonNode respuestaDeCreacion) {
      throw new IllegalStateException("No se llega aquí.");
    }

    @Override
    public Optional<List<TarifaEnvio>> tarifasSiCompleto(JsonNode respuesta, Instant ahora) {
      throw new IllegalStateException("No se llega aquí.");
    }
  }

  // --- rastreo ------------------------------------------------------------------------------

  private final List<String> consultasDeRastreo = new ArrayList<>();

  private SkydropxClient clienteDeRastreo(int estado, String cuerpo) throws IOException {
    servidor = HttpServer.create(new InetSocketAddress(0), 0);
    servidor.createContext(
        "/api/v1/oauth/token",
        intercambio -> {
          peticionesDeToken.incrementAndGet();
          responder(intercambio, 200, token(7200));
        });
    servidor.createContext(
        "/api/v1/shipments/tracking",
        intercambio -> {
          consultasDeRastreo.add(intercambio.getRequestURI().getQuery());
          responder(intercambio, estado, cuerpo);
        });
    servidor.start();

    return new SkydropxClient(
        URI.create("http://localhost:" + servidor.getAddress().getPort()),
        "id-de-prueba",
        "secreto-de-prueba",
        ORIGEN,
        TOPE,
        8,
        Duration.ofMillis(500),
        reloj,
        new MapeadorDePrueba(),
        new MapeadorDeSeguimientoDePrueba(),
        new MapeadorEmisionSkydropxV2(),
        new LimitadorDePeticiones(Duration.ZERO, System::nanoTime, pausas::add),
        pausas::add,
        HttpClient.newHttpClient());
  }

  /**
   * Los dos parámetros van en la consulta y no en la ruta, y el de la transportadora es su
   * <strong>código</strong>. Medido el 16 de septiembre de 2026: la forma de la ruta que anotaba
   * adr/0022 responde 404, y con el nombre visible también.
   */
  /**
   * Cada fallo dice cual fue, y por eso estas pruebas afirman el motivo y no "vino vacio": el
   * sondeo agotado y el proveedor caido llevan al comprador a cosas distintas, y antes se veian
   * iguales desde aqui y desde el registro (docs/13 6.9).
   */
  private static ResultadoCotizacion.Motivo motivoDe(ResultadoCotizacion resultado) {
    return assertInstanceOf(ResultadoCotizacion.NoSePudoCotizar.class, resultado).motivo();
  }

  private static List<TarifaEnvio> tarifasDe(ResultadoCotizacion resultado) {
    return assertInstanceOf(ResultadoCotizacion.ConTarifas.class, resultado).tarifas();
  }

  @Test
  void consultaElRastreoConLaGuiaYElCodigoEnLaConsulta() throws IOException {
    SkydropxClient cliente =
        clienteDeRastreo(200, "{\"eventos\":[{\"id\":\"ev-1\",\"texto\":\"en ruta\"}]}");

    List<AplicarEventoDeEnvioComando> eventos = cliente.consultar("servientrega", "873837506712");

    assertEquals(
        List.of("tracking_number=873837506712&carrier_name=servientrega"), consultasDeRastreo);
    assertEquals(1, eventos.size());
    assertEquals("873837506712", eventos.get(0).guia());
  }

  /**
   * Un 404 es la respuesta normal de una guía que todavía no se ha movido, no un fallo: medido
   * sobre las cuatro guías emitidas, las tres sin movimiento responden justo eso. Tratarlo como
   * error dejaría la tarea de conciliación registrando problemas todas las noches.
   */
  @Test
  void unRastreoSinEventosTodaviaEsListaVaciaYNoUnFallo() throws IOException {
    SkydropxClient cliente =
        clienteDeRastreo(
            404, "{\"error\":\"No se encontró eventos de rastreo para ese número de guía.\"}");

    assertTrue(cliente.consultar("servientrega", "2269401749").isEmpty());
  }

  /** Igual que la cotización: el proveedor caído es "sin novedad", nunca una excepción. */
  @Test
  void unErrorDelProveedorEsSinNovedad() throws IOException {
    SkydropxClient cliente = clienteDeRastreo(500, "{}");

    assertTrue(cliente.consultar("servientrega", "873837506712").isEmpty());
  }

  @Test
  void unCuerpoIlegibleNoLanza() throws IOException {
    SkydropxClient cliente = clienteDeRastreo(200, "esto no es json");

    assertTrue(cliente.consultar("servientrega", "873837506712").isEmpty());
  }

  /**
   * El token es de la cuenta y se comparte: dos consultas seguidas piden uno solo. Es la razón de
   * que cotizar y rastrear vivan en el mismo cliente y no en dos.
   */
  @Test
  void dosConsultasReutilizanElMismoToken() throws IOException {
    SkydropxClient cliente = clienteDeRastreo(200, "{\"eventos\":[]}");

    cliente.consultar("servientrega", "873837506712");
    cliente.consultar("coordinadora", "CO-1");

    assertEquals(1, peticionesDeToken.get());
    assertEquals(2, consultasDeRastreo.size());
  }
}
