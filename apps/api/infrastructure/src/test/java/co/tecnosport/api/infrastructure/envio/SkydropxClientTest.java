package co.tecnosport.api.infrastructure.envio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.envio.CotizacionEnvio;
import co.tecnosport.api.domain.catalogo.Paquete;
import co.tecnosport.api.domain.compartido.Dinero;
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
 * <p>Lo que se prueba aquí es el <strong>protocolo</strong>, que sí está verificado: el token se
 * pide una vez y se reutiliza, se renueva al vencer, el sondeo se corta por intentos y por tiempo,
 * y cualquier fallo termina en lista vacía. El <strong>mapeo</strong> no está confirmado y por eso
 * entra por constructor: el doble de prueba de abajo implementa la forma que la documentación
 * describe, y el de producción ({@code MapeadorCotizacionPendiente}) falla a propósito — las dos
 * cosas se prueban.
 *
 * <p>Ese doble <strong>no valida</strong> que Skydropx use esos nombres de campo. Sería un
 * autoengaño: un servidor falso que habla el mismo idioma inventado que el cliente pasa siempre. Lo
 * único que demuestra es que la máquina de alrededor funciona.
 */
class SkydropxClientTest {

  private static final Instant AHORA = Instant.parse("2026-09-11T12:00:00Z");
  private static final Duration TOPE = Duration.ofSeconds(10);

  private static final OrigenDespacho ORIGEN =
      new OrigenDespacho("TecnoSport", "+573138816711", "Cra. 26C # 38B-31", "05001", null);

  private static final CotizacionEnvio COTIZACION =
      new CotizacionEnvio(
          new Direccion("11", "Bogotá D.C.", "11001", "Bogotá", "Cra. 7 #12-34", null),
          List.of(new Paquete(180, 30, 25, 4)));

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

    List<TarifaEnvio> tarifas = cliente.cotizar(COTIZACION);

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

    List<TarifaEnvio> tarifas = cliente.cotizar(COTIZACION);

    assertTrue(tarifas.isEmpty());
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

    List<TarifaEnvio> tarifas = cliente.cotizar(COTIZACION);

    assertTrue(tarifas.isEmpty());
    assertEquals(1, sondeos.get());
  }

  @Test
  void unaCotizacionQueCompletaSinTarifasDevuelveVacioSinReintentar() throws IOException {
    SkydropxClient cliente =
        clienteContra(
            token(7200), 200, "{\"id\":\"q1\"}", numero -> "{\"is_completed\":true,\"rates\":[]}");

    assertTrue(cliente.cotizar(COTIZACION).isEmpty());
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

    assertTrue(cliente.cotizar(COTIZACION).isEmpty());
    assertEquals(0, sondeos.get());
  }

  @Test
  void unaCreacionSinIdDevuelveVacioSinSondear() throws IOException {
    SkydropxClient cliente = clienteContra(token(7200), 200, "{}", numero -> UNA_TARIFA);

    assertTrue(cliente.cotizar(COTIZACION).isEmpty());
    assertEquals(0, sondeos.get());
  }

  /**
   * El mapeador de producción todavía no se puede escribir, y su excepción no puede escaparse: para
   * el checkout tiene que ser indistinguible de "el proveedor no respondió" — sin envío a
   * domicilio, solo recogida en el punto (adr/0021).
   */
  @Test
  void conElMapeadorPendienteLaCotizacionFallaCerradaYNoLanza() throws IOException {
    SkydropxClient cliente =
        clienteContra(
            token(7200),
            200,
            "{\"id\":\"q1\"}",
            numero -> UNA_TARIFA,
            new MapeadorCotizacionPendiente(),
            8);

    assertTrue(cliente.cotizar(COTIZACION).isEmpty());
    assertEquals(0, sondeos.get());
  }
}
