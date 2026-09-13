package co.tecnosport.api.application.envio;

import static org.junit.jupiter.api.Assertions.assertEquals;

import co.tecnosport.api.application.pedido.MarcarEntregado;
import co.tecnosport.api.application.pedido.RechazarEnEntrega;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.GeneradorIdentificador;
import co.tecnosport.api.domain.envio.Envio;
import co.tecnosport.api.domain.envio.EstadoEnvio;
import co.tecnosport.api.domain.envio.EventoSeguimiento;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * La red por debajo del webhook (adr/0022). Lo que importa aquí no es que aplique eventos —de eso
 * responde {@code AplicarEventoDeEnvioTest}— sino que <strong>no se caiga ni se atasque</strong>:
 * un proveedor que no responde, un evento que ya estaba, un lote con envíos que no interesan.
 */
class ConciliarEnviosTest {

  private static final Instant AHORA = Instant.parse("2026-09-12T15:00:00Z");
  private static final Duration ANTIGUEDAD = Duration.ofHours(12);
  private static final int MAXIMO = 25;

  private RepositorioEnviosFalso envios;
  private List<String> guiasConsultadas;

  @BeforeEach
  void preparar() {
    envios = new RepositorioEnviosFalso();
    guiasConsultadas = new ArrayList<>();
  }

  private ConciliarEnvios conConsultor(ConsultorDeSeguimiento consultor) {
    RepositorioPedidosFalso pedidos = new RepositorioPedidosFalso();
    RepositorioInventarioFalso inventarios = new RepositorioInventarioFalso();
    AplicarEventoDeEnvio aplicar =
        new AplicarEventoDeEnvio(
            envios,
            pedidos,
            new MarcarEntregado(pedidos, inventarios, () -> AHORA),
            new RechazarEnEntrega(pedidos, inventarios, () -> AHORA),
            () -> AHORA);
    return new ConciliarEnvios(envios, consultor, aplicar, () -> AHORA, ANTIGUEDAD, MAXIMO);
  }

  private Envio sembrarEnvioCallado(String guia) {
    Envio envio =
        Envio.crear(
            UUID.randomUUID(),
            "99 minutes",
            guia,
            Dinero.deCop(10_540),
            AHORA.minusSeconds(86_400));
    envios.guardar(envio);
    return envio;
  }

  private ConsultorDeSeguimiento queDevuelve(AplicarEventoDeEnvioComando... eventos) {
    return (transportadora, guia) -> {
      guiasConsultadas.add(guia);
      return List.of(eventos);
    };
  }

  private static AplicarEventoDeEnvioComando evento(String guia, String idExterno) {
    return new AplicarEventoDeEnvioComando(
        guia,
        EstadoEnvio.EN_TRANSITO,
        "en ruta",
        AHORA.minusSeconds(3600),
        idExterno,
        "conciliacion");
  }

  @Test
  void sinEnviosCalladosNoConsultaNada() {
    ConciliarEnvios caso = conConsultor(queDevuelve());

    ResultadoConciliacionEnvios resultado = caso.ejecutar();

    assertEquals(new ResultadoConciliacionEnvios(0, 0, 0), resultado);
    assertEquals(List.of(), guiasConsultadas);
  }

  @Test
  void unEnvioCalladoSeConsultaYSuEventoSeRegistra() {
    sembrarEnvioCallado("NN-1");
    ConciliarEnvios caso = conConsultor(queDevuelve(evento("NN-1", "ev-1")));

    ResultadoConciliacionEnvios resultado = caso.ejecutar();

    assertEquals(new ResultadoConciliacionEnvios(1, 1, 0), resultado);
    assertEquals(List.of("NN-1"), guiasConsultadas);
    assertEquals(1, envios.buscarPorGuia("NN-1").orElseThrow().eventos().size());
  }

  /**
   * Lo que hace hoy el consultor de producción, y lo que hace cualquier proveedor caído: devolver
   * nada. La tarea tiene que terminar bien y dejarlo para la próxima vuelta.
   */
  @Test
  void unConsultorQueNoDevuelveNadaEsSinNovedad() {
    sembrarEnvioCallado("NN-1");
    ConciliarEnvios caso = conConsultor(queDevuelve());

    assertEquals(new ResultadoConciliacionEnvios(1, 0, 1), caso.ejecutar());
  }

  /**
   * Un envío que acaba de recibir un evento deja de estar callado, así que la vuelta siguiente ni
   * lo mira. Es lo que evita que la conciliación consulte en bucle al mismo paquete.
   */
  @Test
  void unEnvioQueYaRecibioUnEventoDejaDeEstarCallado() {
    sembrarEnvioCallado("NN-1");
    conConsultor(queDevuelve(evento("NN-1", "ev-1"))).ejecutar();

    ResultadoConciliacionEnvios segunda =
        conConsultor(queDevuelve(evento("NN-1", "ev-2"))).ejecutar();

    assertEquals(new ResultadoConciliacionEnvios(0, 0, 0), segunda);
    assertEquals(List.of("NN-1"), guiasConsultadas);
  }

  /**
   * El caso normal cuando el webhook sí llegó hace rato: el envío volvió a quedarse callado, se
   * consulta, y la transportadora cuenta lo mismo que ya sabíamos. No es novedad.
   */
  @Test
  void unEventoQueYaEstabaNoCuentaComoNovedad() {
    Envio envio = sembrarEnvioCallado("NN-1");
    envio.registrarEvento(
        new EventoSeguimiento(
            GeneradorIdentificador.nuevo(),
            EstadoEnvio.EN_TRANSITO,
            "en ruta",
            AHORA.minusSeconds(90_000),
            AHORA.minusSeconds(86_400),
            "ev-viejo"));
    envios.guardar(envio);

    ResultadoConciliacionEnvios resultado =
        conConsultor(queDevuelve(evento("NN-1", "ev-viejo"))).ejecutar();

    assertEquals(new ResultadoConciliacionEnvios(1, 0, 1), resultado);
    assertEquals(1, envios.buscarPorGuia("NN-1").orElseThrow().eventos().size());
  }

  @Test
  void variosEnviosSeRevisanTodosAunqueUnoNoTengaNovedad() {
    sembrarEnvioCallado("NN-1");
    sembrarEnvioCallado("NN-2");
    ConciliarEnvios caso =
        conConsultor(
            (transportadora, guia) -> {
              guiasConsultadas.add(guia);
              return "NN-1".equals(guia) ? List.of(evento("NN-1", "ev-1")) : List.of();
            });

    ResultadoConciliacionEnvios resultado = caso.ejecutar();

    assertEquals(new ResultadoConciliacionEnvios(2, 1, 1), resultado);
    assertEquals(2, guiasConsultadas.size());
  }
}
