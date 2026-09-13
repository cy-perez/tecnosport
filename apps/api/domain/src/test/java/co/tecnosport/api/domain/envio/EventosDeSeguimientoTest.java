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
 */
class EventosDeSeguimientoTest {

  private static final Instant DESPACHO = Instant.parse("2026-09-10T14:00:00Z");

  private Envio envio() {
    return Envio.crear(UUID.randomUUID(), "99 minutes", "GUIA-1", Dinero.deCop(10_540), DESPACHO);
  }

  private static EventoSeguimiento evento(
      EstadoEnvio estado, String idExterno, Instant ocurrioEn, Instant recibidoEn) {
    return new EventoSeguimiento(
        GeneradorIdentificador.nuevo(), estado, "descripción", ocurrioEn, recibidoEn, idExterno);
  }

  @Test
  void unEnvioRecienDespachadoNoTieneEventos() {
    Envio envio = envio();

    assertTrue(envio.eventos().isEmpty());
    assertTrue(envio.ultimoEstado().isEmpty());
  }

  @Test
  void registrarUnEventoLoDejaEnElRastro() {
    Envio envio = envio();

    assertTrue(envio.registrarEvento(evento(EstadoEnvio.RECOGIDO, "ev-1", DESPACHO, DESPACHO)));

    assertEquals(1, envio.eventos().size());
    assertEquals(EstadoEnvio.RECOGIDO, envio.ultimoEstado().orElseThrow());
  }

  /**
   * La razón de ser de la idempotencia: el webhook reintenta. Guardar dos veces el mismo evento
   * haría aplicar dos veces sus efectos, y un {@code ENTREGADO} repetido reabre plazos legales que
   * ya estaban corriendo.
   */
  @Test
  void elMismoEventoDosVecesSeGuardaUnaSola() {
    Envio envio = envio();
    envio.registrarEvento(evento(EstadoEnvio.ENTREGADO, "ev-1", DESPACHO, DESPACHO));

    boolean eraNuevo =
        envio.registrarEvento(
            evento(EstadoEnvio.ENTREGADO, "ev-1", DESPACHO, DESPACHO.plusSeconds(600)));

    assertFalse(eraNuevo, "Un reintento del mismo evento no puede contarse como nuevo.");
    assertEquals(1, envio.eventos().size());
  }

  /**
   * Las transportadoras mandan eventos desordenados y con retraso. Rechazar uno "viejo" sería
   * perder justo el que faltaba para entender qué pasó, así que se aceptan y se ordenan por cuándo
   * ocurrieron, no por cuándo llegaron.
   */
  @Test
  void losEventosSeLeenEnElOrdenEnQueOcurrieron() {
    Envio envio = envio();
    Instant hoy = DESPACHO.plusSeconds(86_400);
    envio.registrarEvento(evento(EstadoEnvio.ENTREGADO, "ev-3", hoy, hoy));
    envio.registrarEvento(evento(EstadoEnvio.RECOGIDO, "ev-1", DESPACHO, hoy));
    envio.registrarEvento(evento(EstadoEnvio.EN_TRANSITO, "ev-2", DESPACHO.plusSeconds(3600), hoy));

    List<EstadoEnvio> estados = envio.eventos().stream().map(EventoSeguimiento::estado).toList();

    assertEquals(
        List.of(EstadoEnvio.RECOGIDO, EstadoEnvio.EN_TRANSITO, EstadoEnvio.ENTREGADO), estados);
    assertEquals(EstadoEnvio.ENTREGADO, envio.ultimoEstado().orElseThrow());
  }

  /**
   * Cuándo pasó y cuándo nos enteramos no son lo mismo, y por eso son dos campos. Un webhook
   * perdido y recuperado por la conciliación llega días después del hecho.
   */
  @Test
  void elEventoDistingueCuandoOcurrioDeCuandoLlego() {
    Envio envio = envio();
    Instant cincoDiasDespues = DESPACHO.plusSeconds(5 * 86_400);
    envio.registrarEvento(evento(EstadoEnvio.ENTREGADO, "ev-1", DESPACHO, cincoDiasDespues));

    EventoSeguimiento guardado = envio.eventos().get(0);

    assertEquals(DESPACHO, guardado.ocurrioEn());
    assertEquals(cincoDiasDespues, guardado.recibidoEn());
  }

  @Test
  void unEventoSinIdentificadorExternoNoSePuedeConstruir() {
    assertThrows(
        ExcepcionDeDominio.class, () -> evento(EstadoEnvio.RECOGIDO, "  ", DESPACHO, DESPACHO));
  }

  /**
   * Los cuatro estados en que el paquete se queda quieto. Si nadie los mira, el comprador se entera
   * antes que el negocio (adr/0022).
   */
  @Test
  void cuatroEstadosPidenOjoHumano() {
    assertTrue(EstadoEnvio.EXCEPCION.exigeRevisionManual());
    assertTrue(EstadoEnvio.RETENIDO.exigeRevisionManual());
    assertTrue(EstadoEnvio.CANCELADO.exigeRevisionManual());
    assertTrue(EstadoEnvio.DESTRUIDO.exigeRevisionManual());

    assertFalse(EstadoEnvio.EN_TRANSITO.exigeRevisionManual());
    assertFalse(EstadoEnvio.ENTREGADO.exigeRevisionManual());
    assertFalse(EstadoEnvio.RECOGIDO.exigeRevisionManual());
  }

  /** Los doce de la plataforma, ni uno más: el enum es el contrato con adr/0022. */
  @Test
  void sonLosDoceEstadosDeLaPlataforma() {
    assertEquals(12, EstadoEnvio.values().length);
  }
}
