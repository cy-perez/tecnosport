package co.tecnosport.api.domain.envio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import co.tecnosport.api.domain.compartido.GeneradorIdentificador;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EmisionDeGuiaTest {

  private static final Instant AHORA = Instant.parse("2026-09-16T23:41:59Z");
  private static final UUID PEDIDO = GeneradorIdentificador.nuevo();
  private static final String TARIFA = "b9b9b9b9-0000-4000-8000-000000000001";

  private static EmisionDeGuia solicitada() {
    return EmisionDeGuia.solicitar(PEDIDO, "Servientrega", TARIFA, "admin:1", AHORA);
  }

  private static EmisionDeGuia enCurso(String... envios) {
    EmisionDeGuia emision = solicitada();
    emision.aceptada(List.of(envios), AHORA);
    return emision;
  }

  /**
   * La fila nace <strong>antes</strong> de la llamada, sin envíos y con la tarifa. Es el orden que
   * hace que una guía pagada nunca quede sin nada que la nombre: el {@code idTarifa} es lo único
   * que la recupera por idempotencia dentro de las 96 horas.
   */
  @Test
  void nace_solicitada_con_la_tarifa_y_sin_envios() {
    EmisionDeGuia emision = solicitada();

    assertEquals(EstadoEmision.SOLICITADA, emision.estado());
    assertTrue(emision.estado().abierta());
    assertFalse(emision.estado().enCurso());
    assertEquals(TARIFA, emision.idTarifa());
    assertEquals("admin:1", emision.actor());
    assertTrue(emision.enviosEnPlataforma().isEmpty());
    assertTrue(emision.resueltaEn().isEmpty());
  }

  @Test
  void aceptarla_le_pone_los_envios_y_la_deja_en_curso() {
    EmisionDeGuia emision = solicitada();

    emision.aceptada(List.of("177d1939"), AHORA.plusSeconds(2));

    assertEquals(EstadoEmision.EN_CURSO, emision.estado());
    assertEquals(List.of("177d1939"), emision.enviosEnPlataforma());
  }

  /** Sin envíos no hay nada que releer: quedaría en curso para siempre. */
  @Test
  void no_se_acepta_sin_envios() {
    EmisionDeGuia emision = solicitada();

    assertThrows(ExcepcionDeDominio.class, () -> emision.aceptada(List.of(), AHORA));
    assertEquals(EstadoEmision.SOLICITADA, emision.estado());
  }

  /** Aceptar dos veces sobrescribiría unos identificadores pagados con otros. */
  @Test
  void no_se_acepta_dos_veces() {
    EmisionDeGuia emision = enCurso("177d1939");

    assertThrows(ExcepcionDeDominio.class, () -> emision.aceptada(List.of("otro"), AHORA));
    assertEquals(List.of("177d1939"), emision.enviosEnPlataforma());
  }

  @Test
  void no_se_acepta_una_emision_sin_tarifa() {
    assertThrows(
        ExcepcionDeDominio.class,
        () -> EmisionDeGuia.solicitar(PEDIDO, "Servientrega", "  ", "admin:1", AHORA));
  }

  @Test
  void no_se_acepta_una_emision_sin_transportadora() {
    assertThrows(
        ExcepcionDeDominio.class,
        () -> EmisionDeGuia.solicitar(PEDIDO, " ", TARIFA, "admin:1", AHORA));
  }

  /** Para algo que gasta dinero, una línea de registro no es auditoría. */
  @Test
  void no_se_acepta_una_emision_sin_actor() {
    assertThrows(
        ExcepcionDeDominio.class,
        () -> EmisionDeGuia.solicitar(PEDIDO, "Servientrega", TARIFA, " ", AHORA));
  }

  @Test
  void resolverla_la_cierra_con_su_estado_y_su_fecha() {
    EmisionDeGuia emision = enCurso("177d1939");

    emision.resolver(EstadoEmision.EMITIDA, null, AHORA.plusSeconds(25));

    assertEquals(EstadoEmision.EMITIDA, emision.estado());
    assertTrue(emision.estado().resuelta());
    assertFalse(emision.estado().abierta());
    assertEquals(AHORA.plusSeconds(25), emision.resueltaEn().orElseThrow());
  }

  @Test
  void una_emision_fallida_guarda_el_motivo() {
    EmisionDeGuia emision = enCurso("e47c61d3");

    emision.resolver(EstadoEmision.FALLIDA, "  CARRIER_RESPONSE_ERROR  ", AHORA.plusSeconds(260));

    assertEquals(EstadoEmision.FALLIDA, emision.estado());
    assertEquals("CARRIER_RESPONSE_ERROR", emision.detalle().orElseThrow());
  }

  /**
   * La que se pidió y nunca llegó a aceptarse solo puede quedar fallida: sin identificadores no hay
   * guía que dar por emitida ni por parcial.
   */
  @Test
  void una_solicitud_que_nunca_se_acepto_solo_puede_quedar_fallida() {
    EmisionDeGuia emision = solicitada();

    assertThrows(
        ExcepcionDeDominio.class, () -> emision.resolver(EstadoEmision.EMITIDA, null, AHORA));
    emision.resolver(EstadoEmision.FALLIDA, "422 datos rechazados", AHORA);
    assertEquals(EstadoEmision.FALLIDA, emision.estado());
  }

  /**
   * "No sabemos si cobró" no es "falló": darlo por fallido invita a reintentarlo, y reintentar
   * sobre un cobro que sí ocurrió paga dos veces. Cuenta como abierta y la resuelve una persona.
   */
  @Test
  void una_llamada_sin_desenlace_queda_indeterminada_y_bloquea() {
    EmisionDeGuia emision = solicitada();

    emision.indeterminada("408 tiempo de espera excedido", AHORA.plusSeconds(40));

    assertEquals(EstadoEmision.INDETERMINADA, emision.estado());
    assertTrue(emision.estado().abierta());
    assertFalse(emision.estado().resuelta());
    assertTrue(emision.estado().exigeOjoHumano());
    assertEquals("408 tiempo de espera excedido", emision.detalle().orElseThrow());
  }

  /** Ningún programa la cierra: puede haber un envío pagado del que no tenemos identificador. */
  @Test
  void una_indeterminada_no_la_resuelve_un_programa() {
    EmisionDeGuia emision = solicitada();
    emision.indeterminada("408", AHORA);

    assertThrows(
        ExcepcionDeDominio.class,
        () -> emision.resolver(EstadoEmision.FALLIDA, "ya está", AHORA.plusSeconds(60)));
    assertEquals(EstadoEmision.INDETERMINADA, emision.estado());
  }

  /**
   * La resolución tiene dos disparadores —la tarea programada y el webhook— y pueden llegar a la
   * vez. El segundo no puede reescribir lo que decidió el primero: sobre una emisión ya cerrada,
   * "volvió a fallar" y "en realidad sí salió" son dos verdades distintas sobre la misma plata.
   */
  @Test
  void una_emision_ya_resuelta_rechaza_un_segundo_cierre() {
    EmisionDeGuia emision = enCurso("8bf880c9");
    emision.resolver(EstadoEmision.EMITIDA, null, AHORA.plusSeconds(25));

    assertThrows(
        ExcepcionDeDominio.class,
        () -> emision.resolver(EstadoEmision.FALLIDA, "tarde", AHORA.plusSeconds(90)));
    assertEquals(EstadoEmision.EMITIDA, emision.estado());
  }

  @Test
  void no_se_puede_resolver_como_un_estado_abierto() {
    EmisionDeGuia emision = enCurso("8bf880c9");

    for (EstadoEmision abierto :
        List.of(EstadoEmision.SOLICITADA, EstadoEmision.EN_CURSO, EstadoEmision.INDETERMINADA)) {
      assertThrows(
          ExcepcionDeDominio.class, () -> emision.resolver(abierto, null, AHORA.plusSeconds(5)));
    }
    assertTrue(emision.resueltaEn().isEmpty());
  }

  /**
   * El multienvío es el único caso en que hay varios: un envío por bulto, cada uno con su guía y su
   * cobro (adr/0031). Se conservan en orden porque es el orden de los bultos.
   */
  @Test
  void el_multienvio_guarda_un_envio_por_bulto() {
    EmisionDeGuia emision = enCurso("8bf880c9", "da585a66");

    assertEquals(List.of("8bf880c9", "da585a66"), emision.enviosEnPlataforma());
  }

  /** Los tres estados en los que puede haber plata comprometida sin desenlace. */
  @Test
  void los_tres_estados_abiertos_son_los_que_bloquean() {
    assertTrue(EstadoEmision.SOLICITADA.abierta());
    assertTrue(EstadoEmision.EN_CURSO.abierta());
    assertTrue(EstadoEmision.INDETERMINADA.abierta());
    assertFalse(EstadoEmision.EMITIDA.abierta());
    assertFalse(EstadoEmision.FALLIDA.abierta());
    assertFalse(EstadoEmision.PARCIAL.abierta());
  }

  @Test
  void parcial_es_resuelta_y_pide_ojo_humano() {
    assertTrue(EstadoEmision.PARCIAL.resuelta());
    assertTrue(EstadoEmision.PARCIAL.exigeOjoHumano());
    assertFalse(EstadoEmision.FALLIDA.exigeOjoHumano());
    assertFalse(EstadoEmision.EMITIDA.exigeOjoHumano());
  }

  /**
   * La salida que le faltaba a una indeterminada. Sin ella el pedido se queda bloqueado para
   * siempre: `resolver` le cierra la puerta a un programa a propósito, y no había ninguna otra.
   */
  @Test
  void una_indeterminada_se_descarta_cuando_la_persona_no_encuentra_el_envio() {
    EmisionDeGuia emision = solicitada();
    emision.indeterminada("la llamada no terminó", AHORA);

    emision.descartadaSinCobro("No aparece en el panel de la plataforma.", AHORA.plusSeconds(60));

    assertEquals(EstadoEmision.FALLIDA, emision.estado());
    assertFalse(emision.estado().abierta());
    assertFalse(emision.estado().exigeOjoHumano());
    assertEquals("No aparece en el panel de la plataforma.", emision.detalle().orElseThrow());
  }

  /**
   * Y la otra mitad: el envío sí estaba, y lo que se perdió fueron sus identificadores. Vuelve a
   * EN_CURSO para que el desenlace lo escriba la plataforma al releer, no lo que alguien tecleó.
   */
  @Test
  void una_indeterminada_se_recupera_con_los_envios_que_alguien_encontro() {
    EmisionDeGuia emision = solicitada();
    emision.indeterminada("la llamada no terminó", AHORA);

    emision.recuperada(List.of("env-encontrado"), AHORA.plusSeconds(60));

    assertEquals(EstadoEmision.EN_CURSO, emision.estado());
    assertEquals(List.of("env-encontrado"), emision.enviosEnPlataforma());
    assertTrue(emision.estado().abierta());
    assertTrue(emision.estado().enCurso());
    // Deja de estar resuelta, porque no lo estaba.
    assertTrue(emision.resueltaEn().isEmpty());
  }

  @Test
  void recuperar_sin_identificadores_no_tiene_sentido_y_no_se_permite() {
    EmisionDeGuia emision = solicitada();
    emision.indeterminada("la llamada no terminó", AHORA);

    assertThrows(ExcepcionDeDominio.class, () -> emision.recuperada(List.of(), AHORA));
    assertEquals(EstadoEmision.INDETERMINADA, emision.estado());
  }

  /**
   * Las dos salidas son solo de la indeterminada. Una emitida no se "descarta por no haber cobro"
   * —hubo— y una fallida no se recupera: la plataforma ya dijo que no.
   */
  @Test
  void las_dos_salidas_son_solo_de_la_indeterminada() {
    EmisionDeGuia emitida = enCurso("env-1");
    emitida.resolver(EstadoEmision.EMITIDA, null, AHORA);

    assertThrows(ExcepcionDeDominio.class, () -> emitida.descartadaSinCobro("no", AHORA));
    assertThrows(ExcepcionDeDominio.class, () -> emitida.recuperada(List.of("env-2"), AHORA));
  }
}
