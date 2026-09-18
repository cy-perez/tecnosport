package co.tecnosport.api.infrastructure.envio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.envio.AplicarEventoDeEnvioComando;
import co.tecnosport.api.application.envio.Bulto;
import co.tecnosport.api.application.envio.CotizacionEnvio;
import co.tecnosport.api.application.envio.ResultadoCancelacion;
import co.tecnosport.api.application.envio.ResultadoCotizacion;
import co.tecnosport.api.application.envio.ResultadoEmision;
import co.tecnosport.api.application.envio.SolicitudDeEmision;
import co.tecnosport.api.domain.catalogo.Paquete;
import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.envio.EstadoEnvio;
import co.tecnosport.api.domain.envio.TarifaEnvio;
import co.tecnosport.api.domain.pedido.Contacto;
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
import tools.jackson.databind.json.JsonMapper;

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

  private static final JsonMapper JSON = JsonMapper.builder().build();

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
    return clienteContra(
        cuerpoToken, estadoCreacion, cuerpoCreacion, sondeo, mapeador, intentos, 200);
  }

  /**
   * {@code estadoSondeo} existe porque la creación y el sondeo no significan lo mismo cuando
   * responden un 4xx: en la creación la plataforma juzga nuestro cuerpo, y en el sondeo ya lo
   * aceptó. Sin poder responder distinto en los dos, esa diferencia no se puede probar.
   */
  private SkydropxClient clienteContra(
      String cuerpoToken,
      int estadoCreacion,
      String cuerpoCreacion,
      RespuestaDeSondeo sondeo,
      MapeadorCotizacionSkydropx mapeador,
      int intentos,
      int estadoSondeo)
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
            responder(intercambio, estadoSondeo, sondeo.cuerpo(sondeos.incrementAndGet()));
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

  /**
   * El defecto que esto arregla. La plataforma responde {@code 422} enumerando los campos que
   * rechazó —esta es la forma medida, el mínimo del valor declarado de adr/0035— y hasta el 17 de
   * septiembre de 2026 eso se contaba como proveedor caído. La diferencia no es cosmética: el
   * comprador recibía "intenta de nuevo" y el reintento trae el mismo rechazo, porque Skydropx
   * deduplica las cotizaciones por contenido (docs/13 §6.9).
   */
  @Test
  void unCuerpoRechazadoNoEsUnProveedorCaido() throws IOException {
    SkydropxClient cliente =
        clienteContra(
            token(7200),
            422,
            "{\"errors\":{\"declared_amount\":[\"debe ser mayor que o igual a 10000\"]}}",
            numero -> UNA_TARIFA);

    assertEquals(
        ResultadoCotizacion.Motivo.DATOS_RECHAZADOS, motivoDe(cliente.cotizar(COTIZACION)));
    assertEquals(0, sondeos.get());
  }

  /**
   * Un {@code 401} también es un 4xx y no es nuestro cuerpo: es un despliegue con credenciales que
   * no sirven. Quien lo mire tiene que ir a Secret Manager, no a buscarle un defecto al carrito.
   */
  @Test
  void unTokenRechazadoAlCrearEsFaltaDeCredenciales() throws IOException {
    SkydropxClient cliente = clienteContra(token(7200), 401, "{}", numero -> UNA_TARIFA);

    assertEquals(
        ResultadoCotizacion.Motivo.SIN_CREDENCIALES, motivoDe(cliente.cotizar(COTIZACION)));
  }

  /**
   * Y un {@code 429} es el límite de dos peticiones por segundo de la cuenta, que sí se arregla
   * reintentando: contarlo como cuerpo rechazado le cerraría el envío a domicilio a un comprador
   * por algo que se resuelve solo.
   */
  @Test
  void elLimiteDePeticionesSigueSiendoUnFalloTemporal() throws IOException {
    SkydropxClient cliente = clienteContra(token(7200), 429, "{}", numero -> UNA_TARIFA);

    assertEquals(
        ResultadoCotizacion.Motivo.PROVEEDOR_NO_DISPONIBLE, motivoDe(cliente.cotizar(COTIZACION)));
  }

  /**
   * En el sondeo el cuerpo <strong>ya fue aceptado</strong>, así que un 4xx de ahí no puede
   * significar "nos rechazaron los datos". Es la mitad de la regla que se olvida fácil: el código
   * de respuesta no dice de quién es el problema si no se sabe qué se estaba preguntando.
   */
  @Test
  void unRechazoEnElSondeoNoSeCuentaComoCuerpoRechazado() throws IOException {
    SkydropxClient cliente =
        clienteContra(
            token(7200),
            200,
            "{\"id\":\"q1\"}",
            numero -> UNA_TARIFA,
            new MapeadorDePrueba(),
            8,
            422);

    assertEquals(
        ResultadoCotizacion.Motivo.PROVEEDOR_NO_DISPONIBLE, motivoDe(cliente.cotizar(COTIZACION)));
  }

  /** Salvo el token: es lo único que puede quedar revocado entre la creación y el sondeo. */
  @Test
  void unTokenRevocadoEnElSondeoEsFaltaDeCredenciales() throws IOException {
    SkydropxClient cliente =
        clienteContra(
            token(7200),
            200,
            "{\"id\":\"q1\"}",
            numero -> UNA_TARIFA,
            new MapeadorDePrueba(),
            8,
            401);

    assertEquals(
        ResultadoCotizacion.Motivo.SIN_CREDENCIALES, motivoDe(cliente.cotizar(COTIZACION)));
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

  // --- cancelación de la guía ------------------------------------------------------------------

  private final List<String> rutasDeCancelacion = new ArrayList<>();
  private final List<String> cuerposDeCancelacion = new ArrayList<>();

  private SkydropxClient clienteDeCancelacion(int estado, String cuerpo) throws IOException {
    servidor = HttpServer.create(new InetSocketAddress(0), 0);
    servidor.createContext(
        "/api/v1/oauth/token",
        intercambio -> {
          peticionesDeToken.incrementAndGet();
          responder(intercambio, 200, token(7200));
        });
    servidor.createContext(
        "/api/v1/shipments/",
        intercambio -> {
          rutasDeCancelacion.add(intercambio.getRequestURI().getPath());
          cuerposDeCancelacion.add(
              new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
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
   * El id va dos veces —en la ruta y en el cuerpo— porque así lo pide la plataforma
   * (docs/13-skydropx-capacidades.md §6.4). Esta prueba existe justo para que nadie lo "limpie"
   * creyendo que es una redundancia nuestra.
   */
  @Test
  void cancelaConElIdEnLaRutaYTambienEnElCuerpo() throws IOException {
    SkydropxClient cliente =
        clienteDeCancelacion(200, "{\"success\":true,\"status\":\"cancelled\"}");

    ResultadoCancelacion resultado = cliente.cancelar("177d1939-abc");

    assertInstanceOf(ResultadoCancelacion.Cancelada.class, resultado);
    assertEquals(List.of("/api/v1/shipments/177d1939-abc/cancellations"), rutasDeCancelacion);
    JsonNode cuerpo = JSON.readTree(cuerposDeCancelacion.getFirst());
    assertEquals("177d1939-abc", cuerpo.path("shipment_id").asString());
    assertEquals("Pedido cancelado", cuerpo.path("reason").asString());
  }

  /**
   * El 422 de la plataforma —"El envío no se puede cancelar"— significa que ya no hay nada que
   * hacer: o se anuló antes, o la transportadora ya lo recogió. Contarlo como fallo llenaría la
   * bandeja de revisión de guías intocables, y haría que reintentar nunca convergiera.
   */
  @Test
  void un422CuentaComoCanceladaPorqueYaNoHayNadaQueHacer() throws IOException {
    SkydropxClient cliente =
        clienteDeCancelacion(422, "{\"error\":\"El envío no se puede cancelar\"}");

    assertInstanceOf(ResultadoCancelacion.Cancelada.class, cliente.cancelar("177d1939-abc"));
  }

  /**
   * Un proveedor caído no es una guía anulada. Aquí sí hace falta decir que no se pudo: al otro
   * lado hay un paquete que puede moverse y un pedido que dice que no debería.
   */
  @Test
  void unProveedorCaidoDiceQueNoSePudo() throws IOException {
    SkydropxClient cliente = clienteDeCancelacion(500, "{}");

    ResultadoCancelacion resultado = cliente.cancelar("177d1939-abc");

    assertEquals(
        "la plataforma respondio 500",
        assertInstanceOf(ResultadoCancelacion.NoSePudo.class, resultado).detalle());
  }

  // --- saldo de la cuenta -----------------------------------------------------------------------

  private SkydropxClient clienteDeSaldo(int estado, String cuerpo) throws IOException {
    servidor = HttpServer.create(new InetSocketAddress(0), 0);
    servidor.createContext(
        "/api/v1/oauth/token",
        intercambio -> {
          peticionesDeToken.incrementAndGet();
          responder(intercambio, 200, token(7200));
        });
    servidor.createContext(
        "/api/v1/finance/credits", intercambio -> responder(intercambio, estado, cuerpo));
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
   * El cuerpo es el que respondió la cuenta real el 17 de septiembre de 2026, con el saldo
   * <strong>dentro de {@code data}</strong>. Leerlo un nivel más arriba devolvería vacío sin ningún
   * error, que es la forma en que este proveedor ya ha costado cuatro sesiones.
   */
  @Test
  void leeElSaldoDeDentroDeData() throws IOException {
    SkydropxClient cliente =
        clienteDeSaldo(200, "{\"data\":{\"balance\":10088,\"currency\":\"COP\"}}");

    assertEquals(Optional.of(Dinero.deCop(10_088)), cliente.saldo());
  }

  /** Un saldo en cero es un dato, no una falla: es el estado en el que no se puede emitir. */
  @Test
  void unSaldoEnCeroSeLeeComoCero() throws IOException {
    SkydropxClient cliente = clienteDeSaldo(200, "{\"data\":{\"balance\":0,\"currency\":\"COP\"}}");

    assertEquals(Optional.of(Dinero.deCop(0)), cliente.saldo());
  }

  /**
   * Otra moneda no se compara contra un umbral en pesos. Devolver el número pelado daría una
   * respuesta tranquilizadora y falsa —10.088 dólares y 10.088 pesos no son lo mismo—, así que se
   * dice que no se sabe, igual que cuando no contesta.
   */
  @Test
  void otraMonedaSeTrataComoNoSaber() throws IOException {
    SkydropxClient cliente =
        clienteDeSaldo(200, "{\"data\":{\"balance\":10088,\"currency\":\"USD\"}}");

    assertTrue(cliente.saldo().isEmpty());
  }

  @Test
  void unProveedorCaidoNoDaSaldo() throws IOException {
    assertTrue(clienteDeSaldo(500, "{}").saldo().isEmpty());
  }

  @Test
  void unCuerpoSinBalanceNoDaSaldo() throws IOException {
    assertTrue(clienteDeSaldo(200, "{\"data\":{\"currency\":\"COP\"}}").saldo().isEmpty());
  }

  // --- emision de la guia -----------------------------------------------------------------------

  private static final SolicitudDeEmision SOLICITUD =
      new SolicitudDeEmision(
          "8b2c1d40-0000-4000-8000-000000000001",
          Direccion.sinBarrio("11", "Bogotá D.C.", "11001", "Bogotá", "Cra. 7 #12-34", null),
          new Contacto("Comprador de prueba", "3001234567"),
          new CorreoElectronico("comprador@example.com"),
          List.of("Ropa deportiva"));

  private SkydropxClient clienteDeEmision(int estado, String cuerpo) throws IOException {
    servidor = HttpServer.create(new InetSocketAddress(0), 0);
    servidor.createContext(
        "/api/v1/oauth/token",
        intercambio -> {
          peticionesDeToken.incrementAndGet();
          responder(intercambio, 200, token(7200));
        });
    servidor.createContext(
        "/api/v2/shipments", intercambio -> responder(intercambio, estado, cuerpo));
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

  private static ResultadoEmision.Motivo motivoDe(ResultadoEmision resultado) {
    return assertInstanceOf(ResultadoEmision.Rechazada.class, resultado).motivo();
  }

  /**
   * Lo que este camino ya hacía bien y no se toca: un {@code 422} de la creación del envío es el
   * pedido o la tarifa, y va con los nombres de los campos rechazados.
   */
  @Test
  void unCuerpoRechazadoAlEmitirSigueSiendoDatosRechazados() throws IOException {
    SkydropxClient cliente =
        clienteDeEmision(422, "{\"errors\":{\"rate_id\":[\"no puede estar en blanco\"]}}");

    assertEquals(ResultadoEmision.Motivo.DATOS_RECHAZADOS, motivoDe(cliente.emitir(SOLICITUD)));
  }

  /**
   * Y lo que hacía mal: este camino metía <em>todo</em> 4xx en datos rechazados, así que un token
   * revocado mandaba a revisar un pedido que estaba bien. Es el mismo defecto que la cotización
   * tenía al revés —ella llamaba caída a un rechazo— y se arreglan juntos porque es una sola regla.
   */
  @Test
  void unTokenRechazadoAlEmitirNoEsUnPedidoMalArmado() throws IOException {
    SkydropxClient cliente = clienteDeEmision(401, "{}");

    assertEquals(ResultadoEmision.Motivo.SIN_CREDENCIALES, motivoDe(cliente.emitir(SOLICITUD)));
  }

  /** El límite de peticiones se insiste, no se diagnostica como pedido malo. */
  @Test
  void elLimiteDePeticionesAlEmitirEsProveedorNoDisponible() throws IOException {
    SkydropxClient cliente = clienteDeEmision(429, "{}");

    assertEquals(
        ResultadoEmision.Motivo.PROVEEDOR_NO_DISPONIBLE, motivoDe(cliente.emitir(SOLICITUD)));
  }
}
