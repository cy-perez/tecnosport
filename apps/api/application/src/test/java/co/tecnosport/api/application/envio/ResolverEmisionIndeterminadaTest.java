package co.tecnosport.api.application.envio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.domain.compartido.GeneradorIdentificador;
import co.tecnosport.api.domain.envio.EmisionDeGuia;
import co.tecnosport.api.domain.envio.EstadoEmision;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * La salida de una emisión indeterminada, que es lo que desbloquea el pedido.
 *
 * <p>Lo que se prueba es que las dos afirmaciones posibles —"el envío no está" y "sí está, es
 * este"— dejan el sistema en un estado del que se puede seguir: en el primer caso el pedido puede
 * emitir otra vez, en el segundo la tarea de siempre tiene algo que releer.
 */
class ResolverEmisionIndeterminadaTest {

  private static final Instant AHORA = Instant.parse("2026-09-17T16:00:00Z");
  private static final UUID PEDIDO = GeneradorIdentificador.nuevo();

  private RepositorioEmisionesFalso emisiones;
  private RepositorioAcusesFalso acuses;
  private ResolverEmisionIndeterminada caso;

  @BeforeEach
  void preparar() {
    emisiones = new RepositorioEmisionesFalso();
    acuses = new RepositorioAcusesFalso();
    caso = new ResolverEmisionIndeterminada(emisiones, acuses, () -> AHORA);
  }

  private EmisionDeGuia sembrarIndeterminada() {
    EmisionDeGuia emision =
        EmisionDeGuia.solicitar(
            PEDIDO, "Coordinadora", "tarifa-1", "admin:7", AHORA.minusSeconds(3600));
    emision.indeterminada("la llamada no terminó", AHORA.minusSeconds(3590));
    emisiones.guardar(emision);
    return emision;
  }

  /**
   * El caso que desbloquea el pedido: nadie cobró, así que no hay nada que perder y se puede volver
   * a emitir. Antes de esto, la emisión se quedaba abierta para siempre y con ella el pedido.
   */
  @Test
  void sin_cobro_deja_la_emision_fallida_y_el_pedido_libre() {
    EmisionDeGuia emision = sembrarIndeterminada();

    caso.ejecutar(
        new ResolverEmisionIndeterminadaComando(
            emision.id(), VeredictoDeEmision.SIN_COBRO, List.of(), "admin:9", "No aparece."));

    EmisionDeGuia guardada = emisiones.buscarPorId(emision.id()).orElseThrow();
    assertEquals(EstadoEmision.FALLIDA, guardada.estado());
    assertFalse(guardada.estado().abierta());
    assertTrue(emisiones.buscarAbiertaDePedido(PEDIDO).isEmpty());
  }

  /** Y por eso mismo, después de descartarla el pedido admite una emisión nueva. */
  @Test
  void despues_de_descartarla_se_puede_volver_a_emitir_ese_pedido() {
    EmisionDeGuia emision = sembrarIndeterminada();
    caso.ejecutar(
        new ResolverEmisionIndeterminadaComando(
            emision.id(), VeredictoDeEmision.SIN_COBRO, List.of(), "admin:9", null));

    EmisionDeGuia nueva =
        EmisionDeGuia.solicitar(PEDIDO, "Servientrega", "tarifa-2", "admin:9", AHORA);
    emisiones.guardar(nueva);

    assertEquals(nueva.id(), emisiones.buscarAbiertaDePedido(PEDIDO).orElseThrow().id());
  }

  /**
   * El envío sí estaba, y lo que se había perdido eran sus identificadores. Vuelve a EN_CURSO, que
   * es el estado del que la tarea de resolución sabe tirar.
   */
  @Test
  void con_envio_devuelve_la_emision_a_en_curso_con_lo_que_encontro() {
    EmisionDeGuia emision = sembrarIndeterminada();

    caso.ejecutar(
        new ResolverEmisionIndeterminadaComando(
            emision.id(),
            VeredictoDeEmision.CON_ENVIO,
            List.of("env-encontrado"),
            "admin:9",
            "Estaba en el panel."));

    EmisionDeGuia guardada = emisiones.buscarPorId(emision.id()).orElseThrow();
    assertEquals(EstadoEmision.EN_CURSO, guardada.estado());
    assertEquals(List.of("env-encontrado"), guardada.enviosEnPlataforma());
    assertTrue(guardada.estado().enCurso());
    // Y la tarea de siempre ya la ve, sin que nadie más tenga que hacer nada.
    assertEquals(1, emisiones.buscarEnCurso(10).size());
  }

  @Test
  void decir_que_el_envio_esta_sin_decir_cual_no_resuelve_nada() {
    EmisionDeGuia emision = sembrarIndeterminada();

    assertThrows(
        AcuseNoAplicableException.class,
        () ->
            caso.ejecutar(
                new ResolverEmisionIndeterminadaComando(
                    emision.id(), VeredictoDeEmision.CON_ENVIO, List.of(), "admin:9", null)));

    assertEquals(
        EstadoEmision.INDETERMINADA, emisiones.buscarPorId(emision.id()).orElseThrow().estado());
    assertTrue(acuses.guardados().isEmpty());
  }

  /**
   * Resolver deja el mismo rastro que mirar, y en la misma tabla: quién decidió sobre plata
   * comprometida no puede vivir en dos sitios distintos.
   */
  @Test
  void resolver_deja_constancia_de_quien_lo_afirmo() {
    EmisionDeGuia emision = sembrarIndeterminada();

    caso.ejecutar(
        new ResolverEmisionIndeterminadaComando(
            emision.id(), VeredictoDeEmision.SIN_COBRO, List.of(), "admin:9", "Busqué y no está."));

    assertEquals(1, acuses.guardados().size());
    assertEquals("admin:9", acuses.guardados().get(0).actor());
    assertEquals("Busqué y no está.", acuses.guardados().get(0).nota().orElseThrow());
    assertEquals(emision.id(), acuses.guardados().get(0).referencia());
  }

  /** El detalle dice que lo afirmó una persona: el estado FALLIDA por sí solo no lo distingue. */
  @Test
  void el_detalle_deja_escrito_que_lo_reviso_una_persona() {
    EmisionDeGuia emision = sembrarIndeterminada();

    caso.ejecutar(
        new ResolverEmisionIndeterminadaComando(
            emision.id(), VeredictoDeEmision.SIN_COBRO, List.of(), "admin:9", null));

    assertTrue(
        emisiones
            .buscarPorId(emision.id())
            .orElseThrow()
            .detalle()
            .orElseThrow()
            .contains("el envío no existe"));
  }

  @Test
  void una_emision_que_no_existe_no_se_resuelve() {
    assertThrows(
        EmisionNoEncontradaException.class,
        () ->
            caso.ejecutar(
                new ResolverEmisionIndeterminadaComando(
                    UUID.randomUUID(), VeredictoDeEmision.SIN_COBRO, List.of(), "admin:9", null)));
  }
}
