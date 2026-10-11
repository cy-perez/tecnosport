package co.tecnosport.api.application.proveedores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import co.tecnosport.api.application.compartido.RelojFalso;
import co.tecnosport.api.application.proveedores.ApoyoDeIngesta.RepositorioLotesEnMemoria;
import co.tecnosport.api.domain.proveedores.EstadoLote;
import co.tecnosport.api.domain.proveedores.LoteIngesta;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Lo que se quedó sin nadie que lo mueva, sin tocar lo que una instancia viva está procesando. */
class RecuperarIngestasAbandonadasTest {

  private static final Instant AHORA = Instant.parse("2026-10-11T14:43:00Z");
  private static final Duration LATIDO = Duration.ofMinutes(3);
  private static final Duration COLA = Duration.ofMinutes(15);
  private static final UUID PROVEEDOR = UUID.randomUUID();

  private final RepositorioLotesEnMemoria lotes = new RepositorioLotesEnMemoria();
  private final RecuperarIngestasAbandonadas conColaDeAfuera =
      new RecuperarIngestasAbandonadas(lotes, new RelojFalso(AHORA), LATIDO, COLA);

  private LoteIngesta subido(Instant cuando) {
    LoteIngesta lote =
        LoteIngesta.recibirExportacion(PROVEEDOR, "p/" + UUID.randomUUID() + ".zip", cuando);
    lotes.guardar(lote);
    return lote;
  }

  private LoteIngesta tomadoConLatido(Instant ultimoLatido) {
    LoteIngesta lote = subido(AHORA.minusSeconds(3600));
    lote.iniciar(AHORA.minusSeconds(1800));
    lote.latir(ultimoLatido);
    return lote;
  }

  /** El caso del 11 de octubre de 2026, al revés: el que late no se toca; el muerto se retoma. */
  @Test
  void retomaElQueDejoDeLatirYDejaElQueLate() {
    LoteIngesta muerto = tomadoConLatido(AHORA.minus(LATIDO).minusSeconds(1));
    LoteIngesta vivo = tomadoConLatido(AHORA.minusSeconds(30));

    RecuperarIngestasAbandonadas.Resultado resultado = conColaDeAfuera.ejecutar();

    assertEquals(List.of(muerto.id()), resultado.porEncolar());
    assertEquals(1, resultado.retomados());
    assertEquals(EstadoLote.RECIBIDO, muerto.estado());
    assertEquals(1, muerto.reintentos());
    assertEquals(EstadoLote.PROCESANDO, vivo.estado());
  }

  /** A la cuarta se da por fallido, y el que esperaba detrás se encola. */
  @Test
  void sinReintentosQuedaEnErrorYSeEncolaElSiguienteDelProveedor() {
    LoteIngesta muerto = tomadoConLatido(AHORA.minus(LATIDO).minusSeconds(1));
    muerto.restaurarReintentos(LoteIngesta.MAXIMO_DE_REINTENTOS);
    LoteIngesta queEsperaba = subido(AHORA.minusSeconds(60));

    RecuperarIngestasAbandonadas.Resultado resultado = conColaDeAfuera.ejecutar();

    assertEquals(EstadoLote.ERROR, muerto.estado());
    assertEquals(1, resultado.cerrados());
    assertEquals(List.of(queEsperaba.id()), resultado.porEncolar());
  }

  /** Con la cola de afuera, el que lleva demasiado en la cola sin moverse se vuelve a encolar. */
  @Test
  void elEstancadoEnLaColaSeVuelveAEncolarSinCambiarlo() {
    LoteIngesta estancado = subido(AHORA.minus(COLA).minusSeconds(1));
    LoteIngesta reciente = subido(AHORA.minusSeconds(60));

    RecuperarIngestasAbandonadas.Resultado resultado = conColaDeAfuera.ejecutar();

    assertEquals(List.of(estancado.id()), resultado.porEncolar());
    assertEquals(EstadoLote.RECIBIDO, estancado.estado());
    assertEquals(EstadoLote.RECIBIDO, reciente.estado());
    assertEquals(0, lotes.actualizaciones, "volver a encolarlo no escribe nada");
  }

  /** Sin tolerancia en la cola, lo estancado no se toca: solo se recupera lo abandonado. */
  @Test
  void sinToleranciaEnLaColaNoSeReencolaLoEstancado() {
    subido(AHORA.minus(Duration.ofHours(8)));
    RecuperarIngestasAbandonadas soloAbandonados =
        new RecuperarIngestasAbandonadas(lotes, new RelojFalso(AHORA), LATIDO, null);

    assertEquals(List.of(), soloAbandonados.ejecutar().porEncolar());
  }

  /**
   * El que espera detrás de otro del mismo proveedor no está estancado: lo encola quien suelte a
   * ese. Encolarlo cada minuto abriría tareas que no hacen nada.
   */
  @Test
  void elQueEsperaAUnoAnteriorDelProveedorNoSeReencola() {
    LoteIngesta largo = tomadoConLatido(AHORA.minusSeconds(20));
    LoteIngesta queEspera = subido(AHORA.minus(COLA).minusSeconds(1));

    assertEquals(List.of(), conColaDeAfuera.ejecutar().porEncolar());
    assertEquals(EstadoLote.PROCESANDO, largo.estado());
    assertEquals(EstadoLote.RECIBIDO, queEspera.estado());
  }

  /** En pausa nadie lo tiene, así que nadie lo abandonó: espera a que una persona lo reanude. */
  @Test
  void elPausadoNoSeTocaPorViejoQueSea() {
    LoteIngesta pausado = tomadoConLatido(AHORA.minus(Duration.ofDays(2)));
    pausado.pausar();

    assertEquals(List.of(), conColaDeAfuera.ejecutar().porEncolar());
    assertEquals(EstadoLote.PAUSADO, pausado.estado());
  }

  /**
   * Decide sobre la copia releída con bloqueo, no sobre la de la lista: el último latido pudo
   * llegar entre las dos lecturas.
   */
  @Test
  void decideSobreElLoteReleidoYNoSobreElDeLaLista() {
    lotes.devolverCopias = true;
    LoteIngesta lote = tomadoConLatido(AHORA.minus(LATIDO).minusSeconds(1));
    lotes.guardar(lote);
    lotes.antesDeBloquear = () -> lotes.registrarLatido(lote.id(), AHORA.minusSeconds(5));

    RecuperarIngestasAbandonadas.Resultado resultado = conColaDeAfuera.ejecutar();

    assertEquals(0, resultado.retomados());
    assertEquals(EstadoLote.PROCESANDO, lotes.buscarPorId(lote.id()).orElseThrow().estado());
  }

  @Test
  void unaToleranciaNulaOVaciaNoSeAcepta() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new RecuperarIngestasAbandonadas(lotes, new RelojFalso(AHORA), Duration.ZERO, COLA));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new RecuperarIngestasAbandonadas(
                lotes, new RelojFalso(AHORA), LATIDO, Duration.ofSeconds(-1)));
  }
}
