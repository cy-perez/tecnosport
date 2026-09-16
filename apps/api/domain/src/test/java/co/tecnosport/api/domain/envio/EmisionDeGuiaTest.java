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

  private static EmisionDeGuia enCurso(String... envios) {
    return EmisionDeGuia.solicitada(PEDIDO, "Servientrega", TARIFA, List.of(envios), AHORA);
  }

  @Test
  void nace_en_curso_y_sin_fecha_de_resolucion() {
    EmisionDeGuia emision = enCurso("8bf880c9");

    assertEquals(EstadoEmision.EN_CURSO, emision.estado());
    assertTrue(emision.estado().enCurso());
    assertTrue(emision.resueltaEn().isEmpty());
    assertTrue(emision.detalle().isEmpty());
    assertEquals(List.of("8bf880c9"), emision.enviosEnPlataforma());
  }

  /**
   * Un {@code 202} sin envíos no es una emisión aceptada: no habría nada que releer y la emisión se
   * quedaría en curso para siempre, gastando una consulta por vuelta contra un proveedor limitado a
   * dos peticiones por segundo.
   */
  @Test
  void no_se_acepta_una_emision_sin_envios() {
    assertThrows(ExcepcionDeDominio.class, () -> enCurso());
  }

  @Test
  void no_se_acepta_una_emision_sin_tarifa() {
    assertThrows(
        ExcepcionDeDominio.class,
        () -> EmisionDeGuia.solicitada(PEDIDO, "Servientrega", "  ", List.of("8bf880c9"), AHORA));
  }

  /**
   * El nombre visible se guarda aquí porque la respuesta del envío solo trae el código de la
   * plataforma, y quien resuelve la emisión minutos después ya no tiene la tarifa a mano.
   */
  @Test
  void no_se_acepta_una_emision_sin_transportadora() {
    assertThrows(
        ExcepcionDeDominio.class,
        () -> EmisionDeGuia.solicitada(PEDIDO, " ", TARIFA, List.of("8bf880c9"), AHORA));
  }

  @Test
  void resolverla_la_cierra_con_su_estado_y_su_fecha() {
    EmisionDeGuia emision = enCurso("8bf880c9");

    emision.resolver(EstadoEmision.EMITIDA, null, AHORA.plusSeconds(25));

    assertEquals(EstadoEmision.EMITIDA, emision.estado());
    assertTrue(emision.estado().resuelta());
    assertEquals(AHORA.plusSeconds(25), emision.resueltaEn().orElseThrow());
    assertTrue(emision.detalle().isEmpty());
  }

  @Test
  void una_emision_fallida_guarda_el_motivo() {
    EmisionDeGuia emision = enCurso("e47c61d3");

    emision.resolver(EstadoEmision.FALLIDA, "  CARRIER_RESPONSE_ERROR  ", AHORA.plusSeconds(260));

    assertEquals(EstadoEmision.FALLIDA, emision.estado());
    assertEquals("CARRIER_RESPONSE_ERROR", emision.detalle().orElseThrow());
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
  void no_se_puede_resolver_como_en_curso() {
    EmisionDeGuia emision = enCurso("8bf880c9");

    assertThrows(
        ExcepcionDeDominio.class,
        () -> emision.resolver(EstadoEmision.EN_CURSO, null, AHORA.plusSeconds(5)));
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

  @Test
  void parcial_es_resuelta_pero_no_es_fallida() {
    assertTrue(EstadoEmision.PARCIAL.resuelta());
    assertFalse(EstadoEmision.PARCIAL.enCurso());
    assertFalse(EstadoEmision.EN_CURSO.resuelta());
  }
}
