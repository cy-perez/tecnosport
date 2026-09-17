package co.tecnosport.api.domain.envio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import co.tecnosport.api.domain.compartido.GeneradorIdentificador;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AcuseDeRevisionTest {

  private static final Instant AHORA = Instant.parse("2026-09-17T14:20:00Z");
  private static final UUID REFERENCIA = GeneradorIdentificador.nuevo();

  @Test
  void el_acuse_de_una_guia_guarda_quien_miro_y_cuando() {
    AcuseDeRevision acuse =
        AcuseDeRevision.deGuia(REFERENCIA, "admin:7", "La dejaron en oficina.", AHORA);

    assertEquals(TipoDeRevision.GUIA, acuse.tipo());
    assertEquals(REFERENCIA, acuse.referencia());
    assertEquals("admin:7", acuse.actor());
    assertEquals(AHORA, acuse.revisadoEn());
    assertEquals(Optional.of("La dejaron en oficina."), acuse.nota());
  }

  @Test
  void el_acuse_de_una_emision_apunta_a_la_emision() {
    AcuseDeRevision acuse = AcuseDeRevision.deEmision(REFERENCIA, "admin:7", null, AHORA);

    assertEquals(TipoDeRevision.EMISION, acuse.tipo());
    assertEquals(REFERENCIA, acuse.referencia());
  }

  /** La nota es opcional: obligarla llenaría la base de puntos y de "ok". */
  @Test
  void la_nota_en_blanco_es_lo_mismo_que_no_haberla_escrito() {
    assertEquals(
        Optional.empty(), AcuseDeRevision.deGuia(REFERENCIA, "admin:7", "   ", AHORA).nota());
    assertEquals(
        Optional.empty(), AcuseDeRevision.deGuia(REFERENCIA, "admin:7", null, AHORA).nota());
  }

  /**
   * Un acuse sin actor no dice quién miró, y entonces no sirve para lo único que existe: dejar
   * rastro de quién decidió sobre una guía con plata de por medio.
   */
  @Test
  void un_acuse_sin_actor_no_se_puede_construir() {
    assertThrows(
        ExcepcionDeDominio.class, () -> AcuseDeRevision.deGuia(REFERENCIA, "  ", null, AHORA));
    assertThrows(
        ExcepcionDeDominio.class, () -> AcuseDeRevision.deGuia(REFERENCIA, null, null, AHORA));
  }

  @Test
  void un_acuse_sin_referencia_no_se_puede_construir() {
    assertThrows(
        NullPointerException.class, () -> AcuseDeRevision.deGuia(null, "admin:7", null, AHORA));
  }

  /**
   * Los cinco estados que la bandeja va a pedirle a la base, por nombre. La prueba fija el conjunto
   * porque lo que la consulta filtra es esto y no el predicado: si alguien añade un estado que pide
   * ojo humano y no aparece aquí, el que se queda sin ver es un paquete quieto.
   */
  @Test
  void los_nombres_que_exigen_revision_son_los_cinco_del_predicado() {
    assertEquals(
        Set.of("EXCEPCION", "RETENIDO", "CANCELADO", "DESTRUIDO", "FALLIDO"),
        EstadoEnvio.nombresQueExigenRevisionManual());
  }

  /**
   * No coincide con los terminales y conviene que la prueba lo diga: comparten {@code CANCELADO} y
   * {@code DESTRUIDO}, y confundir los dos conjuntos deja la conciliación preguntando por paquetes
   * muertos o la bandeja ciega a los vivos.
   */
  @Test
  void revision_manual_y_terminales_no_son_el_mismo_conjunto() {
    assertTrue(EstadoEnvio.nombresQueExigenRevisionManual().contains("FALLIDO"));
    assertFalse(EstadoEnvio.nombresTerminales().contains("FALLIDO"));
    assertTrue(EstadoEnvio.nombresTerminales().contains("ENTREGADO"));
    assertFalse(EstadoEnvio.nombresQueExigenRevisionManual().contains("ENTREGADO"));
  }

  /**
   * Eran dos y son tres desde que cancelar un pedido anula sus guías. El conjunto se afirma entero
   * y no por contención a propósito: lo que esta prueba cuida es que un estado nuevo que pida ojo
   * humano no aparezca en la bandeja sin que nadie lo haya decidido, y también que ninguno
   * desaparezca de ella por descuido. Que haya que tocarla al añadir uno es el punto.
   */
  @Test
  void los_nombres_de_emision_que_exigen_ojo_humano_son_tres() {
    assertEquals(
        Set.of("INDETERMINADA", "PARCIAL", "SIN_ANULAR"),
        EstadoEmision.nombresQueExigenOjoHumano());
  }
}
