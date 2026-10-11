package co.tecnosport.api.domain.proveedores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LoteIngestaTest {

  private static final Instant T0 = Instant.parse("2026-09-28T15:15:00Z");
  private static final Instant T1 = Instant.parse("2026-09-28T15:15:07Z");
  private static final Instant T2 = Instant.parse("2026-09-28T15:16:30Z");
  private static final UUID PROVEEDOR = UUID.fromString("01a0ca12-ce7f-7ae2-95e8-e6e0127dc7a6");
  private static final String ARCHIVO = "proveedores/01a0ca12/2026/09/exportacion-abc.zip";
  private static final ResumenIngesta RESUMEN = new ResumenIngesta(40, 5, 35, 9, 9, 0, 0, 0, 2);

  private static LoteIngesta recibido() {
    return LoteIngesta.recibirExportacion(PROVEEDOR, ARCHIVO, T0);
  }

  @Test
  void naceRecibidoConElArchivoYSinFechasDeTrabajo() {
    LoteIngesta lote = recibido();

    assertEquals(EstadoLote.RECIBIDO, lote.estado());
    assertEquals(OrigenIngesta.EXPORTACION_CHAT, lote.origen());
    assertEquals(Optional.of(ARCHIVO), lote.referenciaArchivo());
    assertEquals(T0, lote.creadoEn());
    assertEquals(Optional.empty(), lote.iniciadoEn());
    assertEquals(Optional.empty(), lote.terminadoEn());
    assertEquals(Optional.empty(), lote.resumen());
    assertTrue(lote.estaAbierto());
  }

  @Test
  void unaExportacionSinArchivoNoEsUnLote() {
    ExcepcionDeDominio error =
        assertThrows(
            ExcepcionDeDominio.class, () -> LoteIngesta.recibirExportacion(PROVEEDOR, " ", T0));

    assertTrue(error.getMessage().contains("archivo original"), error.getMessage());
  }

  @Test
  void elRecorridoCompletoDejaLasTresFechas() {
    LoteIngesta lote = recibido();

    lote.iniciar(T1);
    assertEquals(EstadoLote.PROCESANDO, lote.estado());
    assertEquals(Optional.of(T1), lote.iniciadoEn());

    lote.terminar(RESUMEN, T2);
    assertEquals(EstadoLote.TERMINADO, lote.estado());
    assertEquals(Optional.of(RESUMEN), lote.resumen());
    assertEquals(Optional.of(T2), lote.terminadoEn());
    assertFalse(lote.estaAbierto());
  }

  /** Dos hilos no pueden tomar el mismo lote: el segundo tiene que reventar, no repetir. */
  @Test
  void soloSeIniciaDesdeLaCola() {
    LoteIngesta lote = recibido();
    lote.iniciar(T1);

    ExcepcionDeDominio error = assertThrows(ExcepcionDeDominio.class, () -> lote.iniciar(T2));

    assertTrue(error.getMessage().contains("PROCESANDO"), error.getMessage());
  }

  @Test
  void noSeTerminaLoQueNoSeEmpezo() {
    LoteIngesta lote = recibido();

    assertThrows(ExcepcionDeDominio.class, () -> lote.terminar(RESUMEN, T2));
  }

  /** El resumen es lo que alguien ya leyó en el panel. No se reescribe. */
  @Test
  void unLoteTerminadoNoSeVuelveATerminarConOtrasCifras() {
    LoteIngesta lote = recibido();
    lote.iniciar(T1);
    lote.terminar(RESUMEN, T2);

    assertThrows(ExcepcionDeDominio.class, () -> lote.terminar(ResumenIngesta.vacio(), T2));
    assertThrows(ExcepcionDeDominio.class, () -> lote.fallar("tarde", T2));
    assertEquals(Optional.of(RESUMEN), lote.resumen());
  }

  @Test
  void puedeFallarDesdeLaColaYDesdeElTrabajo() {
    LoteIngesta enCola = recibido();
    enCola.fallar("La cola de ingestas está llena.", T1);
    assertEquals(EstadoLote.ERROR, enCola.estado());
    assertEquals(Optional.of("La cola de ingestas está llena."), enCola.detalleError());
    assertEquals(Optional.of(T1), enCola.terminadoEn());

    LoteIngesta enTrabajo = recibido();
    enTrabajo.iniciar(T1);
    enTrabajo.fallar("El zip no trae ningún .txt.", T2);
    assertEquals(EstadoLote.ERROR, enTrabajo.estado());
    assertFalse(enTrabajo.estaAbierto());
  }

  @Test
  void noFallaSinMotivo() {
    LoteIngesta lote = recibido();

    ExcepcionDeDominio error = assertThrows(ExcepcionDeDominio.class, () -> lote.fallar("  ", T1));

    assertTrue(error.getMessage().contains("sin decir por qué"), error.getMessage());
    assertEquals(EstadoLote.RECIBIDO, lote.estado());
  }

  @Test
  void alReconstruirUnTerminadoSinResumenNoSeSostiene() {
    assertThrows(
        ExcepcionDeDominio.class,
        () ->
            new LoteIngesta(
                UUID.randomUUID(),
                OrigenIngesta.EXPORTACION_CHAT,
                PROVEEDOR,
                ARCHIVO,
                EstadoLote.TERMINADO,
                null,
                null,
                T0,
                T1,
                T2));
    assertThrows(
        ExcepcionDeDominio.class,
        () ->
            new LoteIngesta(
                UUID.randomUUID(),
                OrigenIngesta.EXPORTACION_CHAT,
                PROVEEDOR,
                ARCHIVO,
                EstadoLote.ERROR,
                null,
                null,
                T0,
                T1,
                T2));
  }

  @Test
  void elResumenNoAdmiteNegativosNiMasRegistradosQueLeidos() {
    assertThrows(ExcepcionDeDominio.class, () -> new ResumenIngesta(1, 0, 0, 0, 0, 0, 0, 0, -1));
    ExcepcionDeDominio error =
        assertThrows(
            ExcepcionDeDominio.class, () -> new ResumenIngesta(10, 6, 5, 0, 0, 0, 0, 0, 0));

    assertTrue(error.getMessage().contains("más mensajes de los que leyó"), error.getMessage());
  }

  private static LoteIngesta procesando() {
    LoteIngesta lote = recibido();
    lote.iniciar(T1);
    return lote;
  }

  /**
   * En pausa nadie lo tiene: el trabajador lo soltó al verla. Reanudar lo devuelve a la cola, y
   * quien lo reanuda lo encola.
   */
  @Test
  void pausadoNadieLoTieneYAlReanudarVuelveALaCola() {
    LoteIngesta lote = procesando();

    lote.pausar();
    assertEquals(EstadoLote.PAUSADO, lote.estado());
    assertTrue(lote.estaAbierto(), "pausado no se elimina");
    assertFalse(lote.enManosDelTrabajador());
    assertTrue(lote.ocupaAlProveedor(), "retiene a los del mismo proveedor: el orden importa");

    lote.reanudar();
    assertEquals(EstadoLote.RECIBIDO, lote.estado());
    assertEquals(Optional.of(T1), lote.iniciadoEn(), "la fecha de inicio no se pierde");
  }

  /** En la cola no hay trabajo que pausar; pausarlo ahí retendría la cola sin aviso. */
  @Test
  void soloSePausaLoQueSeEstaProcesando() {
    assertThrows(ExcepcionDeDominio.class, () -> recibido().pausar());
    assertThrows(ExcepcionDeDominio.class, () -> recibido().reanudar());
    LoteIngesta pausado = procesando();
    pausado.pausar();
    assertThrows(ExcepcionDeDominio.class, pausado::pausar);
  }

  @Test
  void detenerEnLaColaLoCierraEnElActoSinResumen() {
    LoteIngesta lote = recibido();

    lote.pedirDetencion(T1);

    assertEquals(EstadoLote.DETENIDO, lote.estado());
    assertEquals(Optional.of(T1), lote.terminadoEn());
    assertEquals(Optional.empty(), lote.resumen());
    assertFalse(lote.estaAbierto());
    assertThrows(ExcepcionDeDominio.class, () -> lote.iniciar(T2), "el trabajador lo salta");
  }

  /** Detener en curso es una orden: el lote sigue abierto hasta que el trabajador lo suelte. */
  @Test
  void detenerEnCursoEsperaAQueElTrabajadorLoSuelte() {
    LoteIngesta lote = procesando();

    lote.pedirDetencion(T2);
    assertEquals(EstadoLote.DETENIENDO, lote.estado());
    assertTrue(lote.estaAbierto(), "con el hilo escribiendo en él no se puede eliminar");
    assertEquals(Optional.empty(), lote.terminadoEn());

    ResumenIngesta parcial = new ResumenIngesta(40, 5, 35, 9, 3, 0, 0, 0, 0);
    lote.detener(parcial, T2);
    assertEquals(EstadoLote.DETENIDO, lote.estado());
    assertEquals(Optional.of(parcial), lote.resumen());
    assertEquals(Optional.of(T2), lote.terminadoEn());
    assertFalse(lote.estaAbierto());
  }

  @Test
  void loCerradoNoSeDetieneYLoQueNoSePidioNoSeSuelta() {
    LoteIngesta terminado = procesando();
    terminado.terminar(RESUMEN, T2);
    assertThrows(ExcepcionDeDominio.class, () -> terminado.pedirDetencion(T2));

    LoteIngesta detenido = recibido();
    detenido.pedirDetencion(T1);
    assertThrows(ExcepcionDeDominio.class, () -> detenido.pedirDetencion(T2));
    assertThrows(ExcepcionDeDominio.class, () -> detenido.fallar("tarde", T2));

    assertThrows(ExcepcionDeDominio.class, () -> procesando().detener(RESUMEN, T2));
  }

  /** Si el trabajador llegó al final antes de ver la orden, el trabajo está hecho entero. */
  @Test
  void terminaAunqueLeHayanPedidoPausarODetener() {
    LoteIngesta pausado = procesando();
    pausado.pausar();
    pausado.terminar(RESUMEN, T2);
    assertEquals(EstadoLote.TERMINADO, pausado.estado());

    LoteIngesta deteniendo = procesando();
    deteniendo.pedirDetencion(T2);
    deteniendo.terminar(RESUMEN, T2);
    assertEquals(EstadoLote.TERMINADO, deteniendo.estado());
  }

  private static final Duration TOLERANCIA = Duration.ofMinutes(3);

  @Test
  void tomarloEsElPrimerLatido() {
    LoteIngesta lote = recibido();
    assertEquals(Optional.empty(), lote.latidoEn());

    lote.iniciar(T1);

    assertEquals(Optional.of(T1), lote.latidoEn());
  }

  /**
   * El caso del 11 de octubre de 2026: otra instancia lo tomó y sigue latiendo; para la que arranca
   * no está abandonado, aunque su estado sea el mismo que el de uno huérfano.
   */
  @Test
  void conUnLatidoRecienteNoEstaAbandonadoYSinLatirSi() {
    LoteIngesta lote = recibido();
    lote.iniciar(T1);
    Instant ahora = T1.plus(Duration.ofMinutes(10));
    lote.latir(ahora.minusSeconds(30));

    assertFalse(lote.abandonado(ahora, TOLERANCIA));
    assertTrue(lote.abandonado(ahora.plus(TOLERANCIA), TOLERANCIA));
  }

  @Test
  void unLatidoViejoNoReemplazaAUnoMasReciente() {
    LoteIngesta lote = recibido();
    lote.iniciar(T2);

    lote.latir(T1);

    assertEquals(Optional.of(T2), lote.latidoEn());
  }

  /**
   * El que se estaba deteniendo también lo tiene un trabajador; el pausado ya no lo tiene nadie.
   */
  @Test
  void deteniendoSeAbandonaYPausadoNo() {
    LoteIngesta pausado = recibido();
    pausado.iniciar(T1);
    pausado.pausar();
    LoteIngesta deteniendo = recibido();
    deteniendo.iniciar(T1);
    deteniendo.pedirDetencion(T1);
    Instant tarde = T1.plus(TOLERANCIA).plusSeconds(1);

    assertFalse(pausado.abandonado(tarde, TOLERANCIA));
    assertTrue(deteniendo.abandonado(tarde, TOLERANCIA));
  }

  /** En la cola no hay trabajador que deje de latir; cerrado, no hay nada que abandonar. */
  @Test
  void niElRecibidoNiElCerradoSeAbandonan() {
    LoteIngesta enCola = recibido();
    LoteIngesta terminado = recibido();
    terminado.iniciar(T1);
    terminado.terminar(RESUMEN, T2);
    Instant muchoDespues = T2.plus(Duration.ofDays(1));

    assertFalse(enCola.abandonado(muchoDespues, TOLERANCIA));
    assertFalse(terminado.abandonado(muchoDespues, TOLERANCIA));
  }

  /** Los lotes tomados antes de que existiera el latido cuentan desde que se tomaron. */
  @Test
  void sinLatidoGuardadoCuentaDesdeQueSeTomo() {
    LoteIngesta deAntes =
        new LoteIngesta(
            UUID.randomUUID(),
            OrigenIngesta.EXPORTACION_CHAT,
            PROVEEDOR,
            ARCHIVO,
            EstadoLote.PROCESANDO,
            null,
            null,
            T0,
            T1,
            null);

    assertFalse(deAntes.abandonado(T1.plus(TOLERANCIA), TOLERANCIA));
    assertTrue(deAntes.abandonado(T1.plus(TOLERANCIA).plusSeconds(1), TOLERANCIA));
  }

  // --- Por tramos (ADR-0080) ---

  /** Un tramo que no alcanzó devuelve el lote a la cola con lo que va; el siguiente lo retoma. */
  @Test
  void unTramoQueNoAlcanzaDevuelveElLoteALaColaConSuAvance() {
    LoteIngesta lote = procesando();
    assertFalse(lote.lecturaHecha());
    ResumenIngesta avance = new ResumenIngesta(40, 5, 35, 9, 4, 0, 0, 0, 1);

    lote.soltarParaSeguir(avance, T2);

    assertEquals(EstadoLote.RECIBIDO, lote.estado());
    assertEquals(Optional.of(avance), lote.resumen());
    assertTrue(lote.lecturaHecha());
    assertTrue(lote.ocupaAlProveedor(), "en la cola sigue ocupando a su proveedor");
    lote.iniciar(T2.plusSeconds(5));
    assertEquals(Optional.of(T1), lote.iniciadoEn(), "el inicio es el del primer tramo");
    assertEquals(Optional.of(T2.plusSeconds(5)), lote.latidoEn());
  }

  @Test
  void soloVuelveALaColaLoQueSeEstaProcesando() {
    LoteIngesta pausado = procesando();
    pausado.pausar();

    assertThrows(ExcepcionDeDominio.class, () -> recibido().soltarParaSeguir(RESUMEN, T2));
    assertThrows(ExcepcionDeDominio.class, () -> pausado.soltarParaSeguir(RESUMEN, T2));
  }

  @Test
  void elAvanceSeAnotaMientrasEstaAbiertoYNoDespues() {
    LoteIngesta lote = procesando();
    lote.anotarAvance(RESUMEN);
    assertEquals(Optional.of(RESUMEN), lote.resumen());

    lote.terminar(RESUMEN, T2);
    assertThrows(ExcepcionDeDominio.class, () -> lote.anotarAvance(RESUMEN));
  }

  /** En pausa nadie lo retiene: detenerlo lo cierra en el acto, con lo que alcanzó. */
  @Test
  void detenerEnPausaLoCierraEnElActoConSuAvance() {
    LoteIngesta lote = procesando();
    lote.anotarAvance(RESUMEN);
    lote.pausar();

    lote.pedirDetencion(T2);

    assertEquals(EstadoLote.DETENIDO, lote.estado());
    assertEquals(Optional.of(RESUMEN), lote.resumen());
    assertEquals(Optional.of(T2), lote.terminadoEn());
  }

  /** El abandonado vuelve a la cola tres veces; a la cuarta se da por fallido. */
  @Test
  void elAbandonadoSeRetomaTresVecesYALaCuartaFalla() {
    LoteIngesta lote = procesando();

    for (int vez = 1; vez <= LoteIngesta.MAXIMO_DE_REINTENTOS; vez++) {
      assertEquals(LoteIngesta.Recuperacion.RETOMADO, lote.recuperar(T2));
      assertEquals(EstadoLote.RECIBIDO, lote.estado());
      assertEquals(vez, lote.reintentos());
      lote.iniciar(T2);
    }

    assertEquals(LoteIngesta.Recuperacion.FALLIDO, lote.recuperar(T2));
    assertEquals(EstadoLote.ERROR, lote.estado());
    assertEquals(Optional.of(LoteIngesta.MOTIVO_SIN_REINTENTOS), lote.detalleError());
  }

  /** El que se estaba deteniendo se detiene, que es lo que se pidió; no vuelve a la cola. */
  @Test
  void elAbandonadoQueSeDeteniaQuedaDetenidoConSuAvance() {
    LoteIngesta lote = procesando();
    lote.anotarAvance(RESUMEN);
    lote.pedirDetencion(T2);

    assertEquals(LoteIngesta.Recuperacion.DETENIDO, lote.recuperar(T2));

    assertEquals(EstadoLote.DETENIDO, lote.estado());
    assertEquals(Optional.of(RESUMEN), lote.resumen());
  }

  @Test
  void soloSeRecuperaLoQueTieneUnTrabajador() {
    LoteIngesta pausado = procesando();
    pausado.pausar();

    assertThrows(ExcepcionDeDominio.class, () -> recibido().recuperar(T2));
    assertThrows(ExcepcionDeDominio.class, () -> pausado.recuperar(T2));
  }

  /** En la cola se estanca si nadie lo mueve desde la subida o desde el final del último tramo. */
  @Test
  void seEstancaEnLaColaContandoDesdeQueSeMovioPorUltimaVez() {
    LoteIngesta subido = recibido();
    LoteIngesta deUnTramo = procesando();
    Instant finDelTramo = T0.plus(Duration.ofHours(1));
    deUnTramo.soltarParaSeguir(RESUMEN, finDelTramo);
    Duration tolerancia = Duration.ofMinutes(15);
    Instant despues = finDelTramo.plus(Duration.ofMinutes(10));

    assertTrue(subido.estancadoEnLaCola(despues, tolerancia));
    assertFalse(deUnTramo.estancadoEnLaCola(despues, tolerancia));
    assertFalse(procesando().estancadoEnLaCola(despues.plus(Duration.ofDays(1)), tolerancia));
  }

  @Test
  void losReintentosGuardadosNoPuedenSerNegativos() {
    assertThrows(ExcepcionDeDominio.class, () -> recibido().restaurarReintentos(-1));
  }

  @Test
  void dosResumenesSeSuman() {
    ResumenIngesta lectura = new ResumenIngesta(40, 5, 35, 9, 0, 0, 0, 0, 0);
    ResumenIngesta tramo = new ResumenIngesta(0, 0, 0, 0, 4, 1, 0, 2, 3);

    assertEquals(new ResumenIngesta(40, 5, 35, 9, 4, 1, 0, 2, 3), lectura.mas(tramo));
  }

  /** Con la causa del último fallo, el motivo la dice: es lo que hace falta para arreglarlo. */
  @Test
  void sinReintentosElMotivoDiceElUltimoFallo() {
    LoteIngesta lote = procesando();
    lote.restaurarReintentos(LoteIngesta.MAXIMO_DE_REINTENTOS);

    lote.recuperar(T2, "El zip no trae ningún .txt.");

    assertTrue(
        lote.detalleError().orElseThrow().endsWith("El último fallo: El zip no trae ningún .txt."));
  }
}
