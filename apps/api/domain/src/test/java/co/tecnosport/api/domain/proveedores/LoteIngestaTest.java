package co.tecnosport.api.domain.proveedores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
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
}
