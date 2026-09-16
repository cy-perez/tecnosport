package co.tecnosport.api.application.envio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.application.pedido.MarcarEntregado;
import co.tecnosport.api.application.pedido.RechazarEnEntrega;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.envio.Envio;
import co.tecnosport.api.domain.envio.EstadoEnvio;
import co.tecnosport.api.domain.envio.GuiaEnvio;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * El orden de las puertas del webhook: primero la firma, después la lectura, y solo entonces el
 * efecto. Importa el orden y no solo el resultado — este endpoint es público, y sin la firma
 * delante cualquiera marcaría un pedido como entregado mandando un JSON, con lo que eso arranca de
 * plazos legales.
 *
 * <p>Y una puerta más desde adr/0032: el aviso no trae el evento, así que después de leer la guía
 * hay que <strong>preguntar</strong> por ella. Lo que se prueba aquí es que el webhook consulta la
 * guía de la que habla el aviso y ninguna otra.
 */
class RecibirEventoDeEnvioTest {

  private static final Instant AHORA = Instant.parse("2026-09-12T15:00:00Z");
  private static final Instant DESPACHO = AHORA.minusSeconds(86_400);

  private final RepositorioEnviosFalso envios = new RepositorioEnviosFalso();
  private final List<String> guiasConsultadas = new ArrayList<>();
  private String cuerpoQueLlegoAlLector;

  private RecibirEventoDeEnvio conPuertas(
      VerificadorFirmaEnvio verificador, LectorEventoDeEnvio lector) {
    return conPuertas(verificador, lector, eventoDePrueba());
  }

  private RecibirEventoDeEnvio conPuertas(
      VerificadorFirmaEnvio verificador,
      LectorEventoDeEnvio lector,
      AplicarEventoDeEnvioComando... queDevuelveElRastreo) {
    RepositorioPedidosFalso pedidos = new RepositorioPedidosFalso();
    RepositorioInventarioFalso inventarios = new RepositorioInventarioFalso();
    AplicarEventoDeEnvio aplicar =
        new AplicarEventoDeEnvio(
            envios,
            pedidos,
            new MarcarEntregado(pedidos, inventarios, () -> AHORA),
            new RechazarEnEntrega(pedidos, inventarios, () -> AHORA),
            () -> AHORA);
    ConsultorDeSeguimiento consultor =
        (codigoTransportadora, guia) -> {
          guiasConsultadas.add(codigoTransportadora + "/" + guia);
          return List.of(queDevuelveElRastreo);
        };
    return new RecibirEventoDeEnvio(
        verificador, lector, envios, new ConciliarGuia(consultor, aplicar));
  }

  private static AplicarEventoDeEnvioComando eventoDePrueba() {
    return new AplicarEventoDeEnvioComando(
        "NN-1", EstadoEnvio.EN_TRANSITO, "en ruta", AHORA, "ev-1", "skydropx");
  }

  /** Una guía emitida por la plataforma: sabemos con qué código preguntarle al rastreo. */
  private void sembrarEnvioConGuia(String numero, String codigo) {
    envios.guardar(
        Envio.crear(
            UUID.randomUUID(),
            List.of(GuiaEnvio.crear("99 minutes", codigo, numero, Dinero.deCop(10_540))),
            DESPACHO));
  }

  private static LectorEventoDeEnvio queLee(String guia) {
    return cuerpo -> new LecturaDeEvento.DeUnaGuia(guia);
  }

  // ---------- la firma, primero ----------

  @Test
  void unaFirmaQueNoSeVerificaDescartaElEvento() {
    RecibirEventoDeEnvio caso =
        conPuertas(
            (cuerpo, firma) -> false,
            cuerpo -> {
              cuerpoQueLlegoAlLector = cuerpo;
              return new LecturaDeEvento.DeUnaGuia("NN-1");
            });

    ResultadoEventoDeEnvio resultado = caso.ejecutar("{\"lo que sea\":1}", "HMAC loquesea");

    assertEquals(ResultadoEventoDeEnvio.FIRMA_INVALIDA, resultado);
    assertNull(cuerpoQueLlegoAlLector, "El cuerpo no puede llegar al lector sin firma válida.");
    assertTrue(guiasConsultadas.isEmpty(), "Sin firma no se le pregunta nada al proveedor.");
  }

  /** Un cuerpo sin firma es un cuerpo sin firmar, no un error de petición: se descarta igual. */
  @Test
  void sinCabeceraDeFirmaTambienSeDescarta() {
    RecibirEventoDeEnvio caso = conPuertas((cuerpo, firma) -> firma != null, queLee("NN-1"));

    assertEquals(ResultadoEventoDeEnvio.FIRMA_INVALIDA, caso.ejecutar("{\"lo que sea\":1}", null));
  }

  @Test
  void unCuerpoNuloSeDescartaSinLlamarAlVerificador() {
    RecibirEventoDeEnvio caso =
        conPuertas(
            (cuerpo, firma) -> {
              throw new AssertionError("No se debe verificar un cuerpo nulo.");
            },
            queLee("NN-1"));

    assertEquals(ResultadoEventoDeEnvio.FIRMA_INVALIDA, caso.ejecutar(null, "firma"));
  }

  /**
   * El cuerpo tiene que llegar crudo al verificador: el HMAC se calcula sobre los bytes que
   * llegaron, no sobre un JSON reserializado.
   */
  @Test
  void elCuerpoLlegaCrudoAlVerificador() {
    String[] visto = new String[1];
    RecibirEventoDeEnvio caso =
        conPuertas(
            (cuerpo, firma) -> {
              visto[0] = cuerpo;
              return false;
            },
            queLee("NN-1"));

    String crudo = "{ \"b\": 2,   \"a\": 1 }";
    caso.ejecutar(crudo, "firma");

    assertEquals(crudo, visto[0]);
  }

  // ---------- la lectura, después ----------

  @Test
  void unCuerpoQueNoSeSabeLeerSeDescartaDespuesDeLaFirma() {
    RecibirEventoDeEnvio caso =
        conPuertas((cuerpo, firma) -> true, cuerpo -> new LecturaDeEvento.Ilegible());

    assertEquals(
        ResultadoEventoDeEnvio.NO_SE_PUDO_LEER, caso.ejecutar("{\"forma\":\"desconocida\"}", "ok"));
    assertTrue(guiasConsultadas.isEmpty());
  }

  /**
   * Un aviso de otra cosa de la plataforma no es un cuerpo roto: se entendió, y no habla de un
   * paquete. Se separa de {@link ResultadoEventoDeEnvio#NO_SE_PUDO_LEER} para que el registro no
   * avise de una falla cuando no la hubo. La suscripción de esta cuenta no manda ninguno hoy —once
   * eventos, los once de paquetes—, y el filtro sigue siendo necesario igual: sin él, el
   * identificador que traiga dentro un evento ajeno se leería como una guía.
   */
  @Test
  void unEventoDeOtroTipoNoEsUnCuerpoRotoYNoPreguntaNada() {
    sembrarEnvioConGuia("NN-1", "ninetynineminutes");
    RecibirEventoDeEnvio caso =
        conPuertas((cuerpo, firma) -> true, cuerpo -> new LecturaDeEvento.DeOtroTipo("quotation"));

    assertEquals(ResultadoEventoDeEnvio.EVENTO_DE_OTRO_TIPO, caso.ejecutar("{\"data\":{}}", "ok"));
    assertTrue(guiasConsultadas.isEmpty(), "Un evento ajeno no gasta cuota del proveedor.");
  }

  @Test
  void unaGuiaQueNoEsNuestraSeDescartaSinPreguntarNada() {
    sembrarEnvioConGuia("NN-1", "ninetynineminutes");
    RecibirEventoDeEnvio caso = conPuertas((cuerpo, firma) -> true, queLee("DE-OTRO"));

    assertEquals(ResultadoEventoDeEnvio.GUIA_DESCONOCIDA, caso.ejecutar("{\"x\":1}", "ok"));
    assertTrue(guiasConsultadas.isEmpty());
  }

  // ---------- y solo entonces, preguntar ----------

  /**
   * El corazón de adr/0032: el aviso no trae el evento, así que el webhook pregunta por el rastreo
   * de esa guía —con su código de transportadora— y aplica lo que venga. El evento que se registra
   * es el del rastreo, con su identificador y su fecha, que es el mismo que escribiría la tarea
   * programada.
   */
  @Test
  void conFirmaValidaYGuiaConocidaConsultaElRastreoDeEsaGuia() {
    sembrarEnvioConGuia("NN-1", "ninetynineminutes");
    RecibirEventoDeEnvio caso = conPuertas((cuerpo, firma) -> true, queLee("NN-1"));

    ResultadoEventoDeEnvio resultado = caso.ejecutar("{\"x\":1}", "ok");

    assertEquals(ResultadoEventoDeEnvio.REGISTRADO, resultado);
    assertEquals(List.of("ninetynineminutes/NN-1"), guiasConsultadas);
  }

  /**
   * Un rastreo que no trae nada nuevo no es un fallo. Pasa cada vez que el webhook llega después de
   * que la conciliación ya escribió el evento, que con dos caminos vivos es lo normal.
   */
  @Test
  void unRastreoSinEventosNuevosEsSinNovedad() {
    sembrarEnvioConGuia("NN-1", "ninetynineminutes");
    RecibirEventoDeEnvio caso =
        conPuertas((cuerpo, firma) -> true, queLee("NN-1"), new AplicarEventoDeEnvioComando[0]);

    assertEquals(ResultadoEventoDeEnvio.REPETIDO, caso.ejecutar("{\"x\":1}", "ok"));
    assertEquals(List.of("ninetynineminutes/NN-1"), guiasConsultadas);
  }

  /**
   * Una guía que tecleó una persona en el panel: es nuestra, pero no sabemos con qué código la
   * conoce la plataforma y no se le puede preguntar. Se distingue de "sin novedad" a propósito —
   * aquí nadie miró nada.
   */
  @Test
  void unaGuiaSinCodigoDeTransportadoraNoSeConsulta() {
    envios.guardar(
        Envio.crear(
            UUID.randomUUID(),
            List.of(GuiaEnvio.crear("Servientrega", "SE-1", Dinero.deCop(8_200))),
            DESPACHO));
    RecibirEventoDeEnvio caso = conPuertas((cuerpo, firma) -> true, queLee("SE-1"));

    assertEquals(
        ResultadoEventoDeEnvio.SIN_CODIGO_DE_TRANSPORTADORA, caso.ejecutar("{\"x\":1}", "ok"));
    assertTrue(guiasConsultadas.isEmpty());
  }
}
