package co.tecnosport.api.application.envio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import co.tecnosport.api.application.pedido.MarcarEntregado;
import co.tecnosport.api.application.pedido.RechazarEnEntrega;
import co.tecnosport.api.domain.envio.EstadoEnvio;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * El orden de las tres puertas del webhook: primero la firma, después la lectura, y solo entonces
 * el efecto. Importa el orden y no solo el resultado — este endpoint es público, y sin la firma
 * delante cualquiera marcaría un pedido como entregado mandando un JSON, con lo que eso arranca de
 * plazos legales.
 */
class RecibirEventoDeEnvioTest {

  private static final Instant AHORA = Instant.parse("2026-09-12T15:00:00Z");

  private String cuerpoQueLlegoAlLector;

  private RecibirEventoDeEnvio conPuertas(
      VerificadorFirmaEnvio verificador, LectorEventoDeEnvio lector) {
    RepositorioEnviosFalso envios = new RepositorioEnviosFalso();
    RepositorioPedidosFalso pedidos = new RepositorioPedidosFalso();
    RepositorioInventarioFalso inventarios = new RepositorioInventarioFalso();
    AplicarEventoDeEnvio aplicar =
        new AplicarEventoDeEnvio(
            envios,
            pedidos,
            new MarcarEntregado(pedidos, inventarios, () -> AHORA),
            new RechazarEnEntrega(pedidos, inventarios, () -> AHORA),
            () -> AHORA);
    return new RecibirEventoDeEnvio(verificador, lector, aplicar);
  }

  private static AplicarEventoDeEnvioComando comandoDePrueba() {
    return new AplicarEventoDeEnvioComando(
        "NN-1", EstadoEnvio.ENTREGADO, "entregado", AHORA, "ev-1", "skydropx");
  }

  /**
   * Lo que hace hoy la implementación de producción, y lo que tiene que seguir haciendo hasta que
   * la firma se pueda verificar contra un evento real.
   */
  @Test
  void unaFirmaQueNoSeVerificaDescartaElEvento() {
    RecibirEventoDeEnvio caso =
        conPuertas(
            (cuerpo, firma) -> false,
            cuerpo -> {
              cuerpoQueLlegoAlLector = cuerpo;
              return Optional.of(comandoDePrueba());
            });

    ResultadoEventoDeEnvio resultado = caso.ejecutar("{\"lo que sea\":1}", "HMAC loquesea");

    assertEquals(ResultadoEventoDeEnvio.FIRMA_INVALIDA, resultado);
    assertNull(cuerpoQueLlegoAlLector, "El cuerpo no puede llegar al lector sin firma válida.");
  }

  /** Un cuerpo sin firma es un cuerpo sin firmar, no un error de petición: se descarta igual. */
  @Test
  void sinCabeceraDeFirmaTambienSeDescarta() {
    RecibirEventoDeEnvio caso =
        conPuertas((cuerpo, firma) -> firma != null, cuerpo -> Optional.of(comandoDePrueba()));

    assertEquals(ResultadoEventoDeEnvio.FIRMA_INVALIDA, caso.ejecutar("{\"lo que sea\":1}", null));
  }

  @Test
  void unCuerpoQueNoSeSabeLeerSeDescartaDespuesDeLaFirma() {
    RecibirEventoDeEnvio caso = conPuertas((cuerpo, firma) -> true, cuerpo -> Optional.empty());

    assertEquals(
        ResultadoEventoDeEnvio.NO_SE_PUDO_LEER, caso.ejecutar("{\"forma\":\"desconocida\"}", "ok"));
  }

  /**
   * Con las dos puertas abiertas, el evento llega a {@link AplicarEventoDeEnvio}. Que termine en
   * guía desconocida es suficiente para probarlo —no hay envío sembrado— y evita repetir aquí el
   * montaje entero de un pedido despachado, que ya prueba {@code AplicarEventoDeEnvioTest}.
   */
  @Test
  void conFirmaValidaYCuerpoLegibleElEventoLlegaAAplicarse() {
    RecibirEventoDeEnvio caso =
        conPuertas((cuerpo, firma) -> true, cuerpo -> Optional.of(comandoDePrueba()));

    assertEquals(
        ResultadoEventoDeEnvio.GUIA_DESCONOCIDA, caso.ejecutar("{\"cualquiera\":1}", "ok"));
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
            cuerpo -> Optional.empty());

    String crudo = "{ \"b\": 2,   \"a\": 1 }";
    caso.ejecutar(crudo, "firma");

    assertEquals(crudo, visto[0]);
  }

  @Test
  void unCuerpoNuloSeDescartaSinLlamarAlVerificador() {
    RecibirEventoDeEnvio caso =
        conPuertas(
            (cuerpo, firma) -> {
              throw new AssertionError("No se debe verificar un cuerpo nulo.");
            },
            cuerpo -> Optional.empty());

    assertEquals(ResultadoEventoDeEnvio.FIRMA_INVALIDA, caso.ejecutar(null, "firma"));
  }
}
