package co.tecnosport.api.domain.retracto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.domain.compartido.CalendarioHabil;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import co.tecnosport.api.domain.compartido.VerdictoPlazo;
import co.tecnosport.api.domain.reintegro.MedioReintegro;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SolicitudRetractoTest {

  private static final UUID PEDIDO = UUID.randomUUID();
  private static final Instant ENTREGA =
      ZonedDateTime.of(2026, 9, 10, 15, 30, 0, 0, PlazoDeRetracto.ZONA).toInstant();

  private static Instant enBogota(int mes, int dia) {
    return ZonedDateTime.of(2026, mes, dia, 10, 0, 0, 0, PlazoDeRetracto.ZONA).toInstant();
  }

  private SolicitudRetracto radicadaEl(Instant cuando) {
    return SolicitudRetracto.radicar(
        PEDIDO, ENTREGA, cuando, "admin:1", null, null, CalendarioHabil.sinFestivosCargados());
  }

  @Test
  void naceRadicadaYSinMotivo() {
    SolicitudRetracto solicitud = radicadaEl(enBogota(9, 14));

    assertEquals(EstadoSolicitudRetracto.RADICADA, solicitud.estado());
    assertTrue(solicitud.motivo().isEmpty());
    assertEquals(PEDIDO, solicitud.pedidoId());
  }

  @Test
  void unMotivoEnBlancoEsNoTenerMotivo() {
    SolicitudRetracto solicitud =
        SolicitudRetracto.radicar(
            PEDIDO,
            ENTREGA,
            enBogota(9, 14),
            "admin:1",
            "   ",
            null,
            CalendarioHabil.sinFestivosCargados());

    assertTrue(solicitud.motivo().isEmpty());
  }

  @Test
  void congelaElVerdictoDelDiaEnQueSeRadico() {
    SolicitudRetracto solicitud = radicadaEl(enBogota(9, 14));

    assertEquals(VerdictoPlazo.EN_PLAZO, solicitud.verdictoAlRadicar());
  }

  @Test
  void radicarFueraDePlazoNoSeBloquea() {
    // Decide una persona, con el dato delante: puede haber un acuerdo comercial o una garantía.
    SolicitudRetracto solicitud =
        SolicitudRetracto.radicar(
            PEDIDO,
            ENTREGA,
            enBogota(10, 30),
            "admin:1",
            null,
            null,
            CalendarioHabil.con(Map.of(2026, Set.of())));

    assertEquals(VerdictoPlazo.VENCIDO, solicitud.verdictoAlRadicar());
    assertEquals(EstadoSolicitudRetracto.RADICADA, solicitud.estado());
  }

  @Test
  void sinQuienRadiqueNoHayConstancia() {
    assertThrows(
        ExcepcionDeDominio.class,
        () ->
            SolicitudRetracto.radicar(
                PEDIDO,
                ENTREGA,
                enBogota(9, 14),
                "  ",
                null,
                null,
                CalendarioHabil.sinFestivosCargados()));
  }

  @Test
  void noSeReembolsaSinHaberRecibidoElProducto() {
    SolicitudRetracto solicitud = radicadaEl(enBogota(9, 14));

    assertThrows(
        ExcepcionDeDominio.class,
        () -> solicitud.transicionar(EstadoSolicitudRetracto.REEMBOLSADA));
  }

  @Test
  void elCaminoCompletoLlegaAReembolsada() {
    SolicitudRetracto solicitud = radicadaEl(enBogota(9, 14));

    solicitud.recibirProducto(enBogota(9, 16));
    solicitud.transicionar(EstadoSolicitudRetracto.REEMBOLSADA);

    assertEquals(EstadoSolicitudRetracto.REEMBOLSADA, solicitud.estado());
  }

  @Test
  void unaSolicitudReembolsadaYaNoSeMueve() {
    SolicitudRetracto solicitud = radicadaEl(enBogota(9, 14));
    solicitud.recibirProducto(enBogota(9, 16));
    solicitud.transicionar(EstadoSolicitudRetracto.REEMBOLSADA);

    assertThrows(
        ExcepcionDeDominio.class, () -> solicitud.transicionar(EstadoSolicitudRetracto.RECHAZADA));
  }

  @Test
  void elPlazoDeReintegroNoCorreMientrasElProductoNoVuelve() {
    SolicitudRetracto solicitud = radicadaEl(enBogota(9, 14));

    assertTrue(solicitud.limiteDeReintegro().isEmpty());
  }

  @Test
  void elPlazoDeReintegroEsDeQuinceDiasCalendarioDesdeQueVuelveElProducto() {
    SolicitudRetracto solicitud = radicadaEl(enBogota(9, 14));
    solicitud.recibirProducto(enBogota(9, 16));

    // Recibido el 16 de septiembre: el plazo se agota al terminar el 1 de octubre.
    assertEquals(
        ZonedDateTime.of(2026, 10, 2, 0, 0, 0, 0, PlazoDeRetracto.ZONA).toInstant(),
        solicitud.limiteDeReintegro().orElseThrow());
  }

  // ---- El medio de pago que prefiere el comprador (Ley 2439 de 2024) ----

  @Test
  void naceSinPreferenciaSiElCompradorNoDijoNada() {
    // No se le puede exigir para radicar: el retracto se ejerce sin condiciones.
    SolicitudRetracto solicitud = radicadaEl(enBogota(9, 14));

    assertTrue(solicitud.medioPreferido().isEmpty());
  }

  @Test
  void sinPreferenciaCualquierMedioLaRespeta() {
    // No se puede incumplir una preferencia que nadie expreso.
    SolicitudRetracto solicitud = radicadaEl(enBogota(9, 14));

    assertTrue(solicitud.respetaLaPreferencia(MedioReintegro.EFECTIVO));
  }

  @Test
  void anotarLaPreferenciaLaGuardaYSoloEseMedioLaRespeta() {
    SolicitudRetracto solicitud = radicadaEl(enBogota(9, 14));

    solicitud.anotarMedioPreferido(MedioReintegro.TRANSFERENCIA_BANCARIA);

    assertEquals(MedioReintegro.TRANSFERENCIA_BANCARIA, solicitud.medioPreferido().orElseThrow());
    assertTrue(solicitud.respetaLaPreferencia(MedioReintegro.TRANSFERENCIA_BANCARIA));
    assertFalse(solicitud.respetaLaPreferencia(MedioReintegro.WOMPI));
  }

  @Test
  void anotarDosVecesElMismoMedioNoMolesta() {
    // El panel puede reenviarlo sin querer, y repetir el mismo hecho no lo cambia.
    SolicitudRetracto solicitud = radicadaEl(enBogota(9, 14));

    solicitud.anotarMedioPreferido(MedioReintegro.WOMPI);
    solicitud.anotarMedioPreferido(MedioReintegro.WOMPI);

    assertEquals(MedioReintegro.WOMPI, solicitud.medioPreferido().orElseThrow());
  }

  @Test
  void unaPreferenciaYaAnotadaNoSeCorrige() {
    // Corregirla borraria la constancia de lo que el comprador pidio, que es justo para lo que
    // existe el campo: si de verdad pidio otra cosa despues, eso es un hecho nuevo.
    SolicitudRetracto solicitud = radicadaEl(enBogota(9, 14));
    solicitud.anotarMedioPreferido(MedioReintegro.TRANSFERENCIA_BANCARIA);

    assertThrows(
        ExcepcionDeDominio.class, () -> solicitud.anotarMedioPreferido(MedioReintegro.EFECTIVO));
  }

  @Test
  void conElDineroYaDevueltoNoSeAnotaNingunaPreferencia() {
    // Anotarla con el pago hecho es escribir el examen viendo las respuestas.
    SolicitudRetracto solicitud = radicadaEl(enBogota(9, 14));
    solicitud.recibirProducto(enBogota(9, 16));
    solicitud.registrarReintegro(UUID.randomUUID());

    assertThrows(
        ExcepcionDeDominio.class, () -> solicitud.anotarMedioPreferido(MedioReintegro.WOMPI));
  }

  @Test
  void recibirElProductoGuardaLaFechaEnLaSolicitud() {
    SolicitudRetracto solicitud = radicadaEl(enBogota(9, 14));

    solicitud.recibirProducto(enBogota(9, 16));

    assertEquals(EstadoSolicitudRetracto.PRODUCTO_RECIBIDO, solicitud.estado());
    assertEquals(enBogota(9, 16), solicitud.productoRecibidoEn().orElseThrow());
  }
}
