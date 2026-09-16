package co.tecnosport.api.domain.envio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import co.tecnosport.api.domain.compartido.GeneradorIdentificador;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * El rastro del paquete, que es lo que se lee el día de la reclamación (adr/0022). Lo que se prueba
 * aquí no es que se guarde: es que <strong>no se pierda ni se duplique</strong>, que son las dos
 * formas en que un registro append-only deja de servir.
 *
 * <p>El rastro es de la guía y no del envío (adr/0031): un pedido puede salir en dos paquetes y
 * cada uno se mueve solo.
 */
class EventosDeSeguimientoTest {

  private static final Instant DESPACHO = Instant.parse("2026-09-10T14:00:00Z");

  private static GuiaEnvio guia() {
    return GuiaEnvio.crear("99 minutes", "GUIA-1", Dinero.deCop(10_540));
  }

  private static Envio envioDeDosPaquetes() {
    return Envio.crear(
        UUID.randomUUID(),
        List.of(guia(), GuiaEnvio.crear("Servientrega", "GUIA-2", Dinero.deCop(8_200))),
        DESPACHO);
  }

  private static EventoSeguimiento evento(
      EstadoEnvio estado, String idExterno, Instant ocurrioEn, Instant recibidoEn) {
    return new EventoSeguimiento(
        GeneradorIdentificador.nuevo(), estado, "descripción", ocurrioEn, recibidoEn, idExterno);
  }

  @Test
  void unEnvioRecienDespachadoNoTieneEventos() {
    Envio envio = envioDeDosPaquetes();

    assertTrue(envio.guias().stream().allMatch(guia -> guia.eventos().isEmpty()));
    assertTrue(envio.ultimoEstado().isEmpty());
  }

  @Test
  void registrarUnEventoLoDejaEnElRastro() {
    GuiaEnvio guia = guia();

    assertTrue(guia.registrarEvento(evento(EstadoEnvio.RECOGIDO, "ev-1", DESPACHO, DESPACHO)));

    assertEquals(1, guia.eventos().size());
    assertEquals(EstadoEnvio.RECOGIDO, guia.ultimoEstado().orElseThrow());
  }

  /**
   * El caso que estrena adr/0031, y el que se rompe solo si alguien vuelve a colgar los eventos del
   * envío: el movimiento de un paquete no puede aparecer en el rastro de su hermano, porque el
   * comprador leería que le entregaron algo que sigue en camino.
   */
  @Test
  void unEventoCaeEnSuGuiaYNoEnLaHermana() {
    Envio envio = envioDeDosPaquetes();

    assertTrue(
        envio.registrarEvento("GUIA-2", evento(EstadoEnvio.ENTREGADO, "ev-1", DESPACHO, DESPACHO)));

    assertTrue(envio.guiaDe("GUIA-1").orElseThrow().eventos().isEmpty());
    assertEquals(1, envio.guiaDe("GUIA-2").orElseThrow().eventos().size());
  }

  /** Una guía que no es de este envío no lanza: el webhook responde 200 igual. */
  @Test
  void unEventoDeUnaGuiaAjenaNoSeRegistraYNoRevienta() {
    Envio envio = envioDeDosPaquetes();

    assertFalse(
        envio.registrarEvento(
            "NO-ES-MIA", evento(EstadoEnvio.ENTREGADO, "ev-1", DESPACHO, DESPACHO)));
  }

  /**
   * La conciliación pregunta por guía, no por envío: con una entregada y otra en tránsito, dar el
   * envío por terminado dejaría la segunda sin conciliar para siempre.
   */
  @Test
  void unaGuiaEntregadaTerminaYLaHermanaSigueViva() {
    Envio envio = envioDeDosPaquetes();
    envio.registrarEvento("GUIA-2", evento(EstadoEnvio.ENTREGADO, "ev-1", DESPACHO, DESPACHO));

    assertTrue(envio.guiaDe("GUIA-2").orElseThrow().terminada());
    assertFalse(envio.guiaDe("GUIA-1").orElseThrow().terminada());
  }

  /**
   * La razón de ser de la idempotencia: el webhook reintenta. Guardar dos veces el mismo evento
   * haría aplicar dos veces sus efectos, y un {@code ENTREGADO} repetido reabre plazos legales que
   * ya estaban corriendo.
   */
  @Test
  void elMismoEventoDosVecesSeGuardaUnaSola() {
    GuiaEnvio guia = guia();
    guia.registrarEvento(evento(EstadoEnvio.ENTREGADO, "ev-1", DESPACHO, DESPACHO));

    boolean eraNuevo =
        guia.registrarEvento(
            evento(EstadoEnvio.ENTREGADO, "ev-1", DESPACHO, DESPACHO.plusSeconds(600)));

    assertFalse(eraNuevo, "Un reintento del mismo evento no puede contarse como nuevo.");
    assertEquals(1, guia.eventos().size());
  }

  /**
   * Las transportadoras mandan eventos desordenados y con retraso. Rechazar uno "viejo" sería
   * perder justo el que faltaba para entender qué pasó, así que se aceptan y se ordenan por cuándo
   * ocurrieron, no por cuándo llegaron.
   */
  @Test
  void losEventosSeLeenEnElOrdenEnQueOcurrieron() {
    GuiaEnvio guia = guia();
    Instant hoy = DESPACHO.plusSeconds(86_400);
    guia.registrarEvento(evento(EstadoEnvio.ENTREGADO, "ev-3", hoy, hoy));
    guia.registrarEvento(evento(EstadoEnvio.RECOGIDO, "ev-1", DESPACHO, hoy));
    guia.registrarEvento(evento(EstadoEnvio.EN_TRANSITO, "ev-2", DESPACHO.plusSeconds(3600), hoy));

    List<EstadoEnvio> estados = guia.eventos().stream().map(EventoSeguimiento::estado).toList();

    assertEquals(
        List.of(EstadoEnvio.RECOGIDO, EstadoEnvio.EN_TRANSITO, EstadoEnvio.ENTREGADO), estados);
    assertEquals(EstadoEnvio.ENTREGADO, guia.ultimoEstado().orElseThrow());
  }

  /**
   * Cuándo pasó y cuándo nos enteramos no son lo mismo, y por eso son dos campos. Un webhook
   * perdido y recuperado por la conciliación llega días después del hecho.
   */
  @Test
  void elEventoDistingueCuandoOcurrioDeCuandoLlego() {
    GuiaEnvio guia = guia();
    Instant cincoDiasDespues = DESPACHO.plusSeconds(5 * 86_400);
    guia.registrarEvento(evento(EstadoEnvio.ENTREGADO, "ev-1", DESPACHO, cincoDiasDespues));

    EventoSeguimiento guardado = guia.eventos().get(0);

    assertEquals(DESPACHO, guardado.ocurrioEn());
    assertEquals(cincoDiasDespues, guardado.recibidoEn());
  }

  @Test
  void unEventoSinIdentificadorExternoNoSePuedeConstruir() {
    assertThrows(
        ExcepcionDeDominio.class, () -> evento(EstadoEnvio.RECOGIDO, "  ", DESPACHO, DESPACHO));
  }

  /**
   * Los cinco estados en que el paquete se queda quieto. Si nadie los mira, el comprador se entera
   * antes que el negocio (adr/0022).
   */
  @Test
  void cincoEstadosPidenOjoHumano() {
    assertTrue(EstadoEnvio.EXCEPCION.exigeRevisionManual());
    assertTrue(EstadoEnvio.RETENIDO.exigeRevisionManual());
    assertTrue(EstadoEnvio.CANCELADO.exigeRevisionManual());
    assertTrue(EstadoEnvio.DESTRUIDO.exigeRevisionManual());
    assertTrue(EstadoEnvio.FALLIDO.exigeRevisionManual());

    assertFalse(EstadoEnvio.EN_TRANSITO.exigeRevisionManual());
    assertFalse(EstadoEnvio.ENTREGADO.exigeRevisionManual());
    assertFalse(EstadoEnvio.RECOGIDO.exigeRevisionManual());
  }

  /**
   * {@code FALLIDO} pide ojo humano y <strong>no</strong> es terminal, que es la combinación menos
   * obvia del enum: parece una guía muerta, pero que el estado sea final es justo lo que no se ha
   * medido. Darlo por terminado dejaría de preguntar por ese envío para siempre.
   */
  @Test
  void elEstadoFallidoNoCierraLaHistoriaDelPaquete() {
    assertFalse(EstadoEnvio.FALLIDO.esTerminal());
    assertFalse(EstadoEnvio.nombresTerminales().contains(EstadoEnvio.FALLIDO.name()));
  }

  /** Los trece de la plataforma, ni uno más: el enum es el contrato con adr/0022. */
  @Test
  void sonLosTreceEstadosDeLaPlataforma() {
    assertEquals(13, EstadoEnvio.values().length);
  }
}
