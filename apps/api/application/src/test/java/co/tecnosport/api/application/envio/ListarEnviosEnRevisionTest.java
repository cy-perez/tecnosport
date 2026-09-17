package co.tecnosport.api.application.envio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.GeneradorIdentificador;
import co.tecnosport.api.domain.compartido.Sku;
import co.tecnosport.api.domain.envio.AcuseDeRevision;
import co.tecnosport.api.domain.envio.EmisionDeGuia;
import co.tecnosport.api.domain.envio.Envio;
import co.tecnosport.api.domain.envio.EstadoEmision;
import co.tecnosport.api.domain.envio.EstadoEnvio;
import co.tecnosport.api.domain.envio.EventoSeguimiento;
import co.tecnosport.api.domain.envio.GuiaEnvio;
import co.tecnosport.api.domain.pedido.Direccion;
import co.tecnosport.api.domain.pedido.EstadoPedido;
import co.tecnosport.api.domain.pedido.LineaPedido;
import co.tecnosport.api.domain.pedido.MetodoPago;
import co.tecnosport.api.domain.pedido.NumeroPedido;
import co.tecnosport.api.domain.pedido.Pedido;
import co.tecnosport.api.domain.pedido.TipoEntrega;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * La bandeja de revisión: lo que se quedó quieto y nadie ha mirado.
 *
 * <p>Lo que se prueba aquí no es que la lista se arme, sino las dos reglas de las que depende que
 * sirva: que un acuse la vacíe, y que un evento nuevo <em>posterior</em> al acuse la vuelva a
 * llenar. Sin la segunda, acusar una guía sería taparla para siempre.
 */
class ListarEnviosEnRevisionTest {

  private static final Instant DESPACHO = Instant.parse("2026-09-10T15:00:00Z");
  private static final Instant AHORA = Instant.parse("2026-09-17T15:00:00Z");

  private static final Direccion MEDELLIN =
      new Direccion("05", "Antioquia", "05001", "Medellín", "Circular 4 # 70-20", null);

  private RepositorioEnviosFalso envios;
  private RepositorioEmisionesFalso emisiones;
  private RepositorioAcusesFalso acuses;
  private RepositorioPedidosFalso pedidos;
  private ListarEnviosEnRevision bandeja;
  private AcusarRevisionDeGuia acusarGuia;
  private AcusarRevisionDeEmision acusarEmision;

  @BeforeEach
  void preparar() {
    envios = new RepositorioEnviosFalso();
    emisiones = new RepositorioEmisionesFalso();
    acuses = new RepositorioAcusesFalso();
    pedidos = new RepositorioPedidosFalso();
    bandeja = new ListarEnviosEnRevision(envios, emisiones, acuses, pedidos);
    acusarGuia = new AcusarRevisionDeGuia(envios, acuses, () -> AHORA);
    acusarEmision = new AcusarRevisionDeEmision(emisiones, acuses, () -> AHORA);
  }

  private Pedido sembrarPedido() {
    Pedido pedido =
        Pedido.crear(
            NumeroPedido.de(2026, 7),
            null,
            new CorreoElectronico("cliente@tecnosport.co"),
            List.of(
                new LineaPedido(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    new Sku("TS-CAM-AZ-M"),
                    "Camiseta running Dry-Fit",
                    1,
                    Dinero.deCop(89_900),
                    new BigDecimal("0.19"),
                    null,
                    UUID.randomUUID())),
            TipoEntrega.ENVIO_A_DOMICILIO,
            MEDELLIN,
            MetodoPago.NEQUI,
            "cliente@tecnosport.co",
            DESPACHO.minusSeconds(3600));
    pedido.transicionar(EstadoPedido.PAGADO, "webhook", "pago aprobado", DESPACHO);
    pedido.transicionar(EstadoPedido.EN_PREPARACION, "admin", "alistado", DESPACHO);
    pedido.transicionar(EstadoPedido.DESPACHADO, "admin", "despachado", DESPACHO);
    pedidos.guardar(pedido);
    return pedido;
  }

  private Envio sembrarEnvioConEvento(EstadoEnvio estado, Instant ocurrioEn, Instant recibidoEn) {
    Pedido pedido = sembrarPedido();
    Envio envio =
        Envio.crear(
            pedido.id(),
            List.of(GuiaEnvio.crear("Servientrega", "NN-1", Dinero.deCop(10_540))),
            DESPACHO);
    envio.registrarEvento("NN-1", evento(estado, ocurrioEn, recibidoEn, "ev-1"));
    envios.guardar(envio);
    return envio;
  }

  private static EventoSeguimiento evento(
      EstadoEnvio estado, Instant ocurrioEn, Instant recibidoEn, String idExterno) {
    return new EventoSeguimiento(
        GeneradorIdentificador.nuevo(),
        estado,
        "novedad de la transportadora",
        ocurrioEn,
        recibidoEn,
        idExterno);
  }

  @Test
  void una_guia_en_excepcion_aparece_con_el_numero_del_pedido() {
    Pedido pedido = sembrarPedido();
    Envio envio =
        Envio.crear(
            pedido.id(),
            List.of(GuiaEnvio.crear("Servientrega", "NN-1", Dinero.deCop(10_540))),
            DESPACHO);
    envio.registrarEvento(
        "NN-1", evento(EstadoEnvio.EXCEPCION, DESPACHO, DESPACHO.plusSeconds(60), "ev-1"));
    envios.guardar(envio);

    BandejaDeRevision resultado = bandeja.ejecutar(50);

    assertEquals(1, resultado.guias().size());
    GuiaEnRevision guia = resultado.guias().get(0);
    assertEquals("NN-1", guia.numeroGuia());
    assertEquals(EstadoEnvio.EXCEPCION, guia.estado());
    assertEquals(pedido.numeroPedido().valor(), guia.numeroPedido());
    assertTrue(guia.revisadaAntesEn().isEmpty());
  }

  /** Un paquete que siguió su camino después de la novedad ya no pide nada. */
  @Test
  void una_guia_que_volvio_a_moverse_no_esta_en_la_bandeja() {
    Pedido pedido = sembrarPedido();
    Envio envio =
        Envio.crear(
            pedido.id(),
            List.of(GuiaEnvio.crear("Servientrega", "NN-1", Dinero.deCop(10_540))),
            DESPACHO);
    envio.registrarEvento(
        "NN-1", evento(EstadoEnvio.EXCEPCION, DESPACHO, DESPACHO.plusSeconds(60), "ev-1"));
    envio.registrarEvento(
        "NN-1",
        evento(
            EstadoEnvio.EN_TRANSITO,
            DESPACHO.plusSeconds(7200),
            DESPACHO.plusSeconds(7260),
            "ev-2"));
    envios.guardar(envio);

    assertTrue(bandeja.ejecutar(50).vacia());
  }

  @Test
  void un_acuse_saca_la_guia_de_la_bandeja() {
    sembrarEnvioConEvento(EstadoEnvio.RETENIDO, DESPACHO, DESPACHO.plusSeconds(60));

    acusarGuia.ejecutar(
        new AcusarRevisionDeGuiaComando("NN-1", "admin:7", "Hablé con la transportadora."));

    assertTrue(bandeja.ejecutar(50).guias().isEmpty());
    assertEquals(1, acuses.guardados().size());
    assertEquals("admin:7", acuses.guardados().get(0).actor());
  }

  /**
   * La regla que hace que el acuse no sea una mordaza. Si la transportadora manda algo nuevo
   * después de que alguien miró, la guía vuelve a pedir atención: lo que se revisó fue el estado de
   * antes, no este.
   */
  @Test
  void un_evento_posterior_al_acuse_devuelve_la_guia_a_la_bandeja() {
    Envio envio = sembrarEnvioConEvento(EstadoEnvio.RETENIDO, DESPACHO, DESPACHO.plusSeconds(60));
    acusarGuia.ejecutar(new AcusarRevisionDeGuiaComando("NN-1", "admin:7", null));
    assertTrue(bandeja.ejecutar(50).guias().isEmpty());

    envio.registrarEvento(
        "NN-1",
        evento(EstadoEnvio.DESTRUIDO, AHORA.plusSeconds(60), AHORA.plusSeconds(120), "ev-2"));
    envios.guardar(envio);

    BandejaDeRevision resultado = bandeja.ejecutar(50);

    assertEquals(1, resultado.guias().size());
    assertEquals(EstadoEnvio.DESTRUIDO, resultado.guias().get(0).estado());
    assertEquals(AHORA, resultado.guias().get(0).revisadaAntesEn().orElseThrow());
  }

  /**
   * El reloj de la transportadora no manda. Un evento que <em>ocurrió</em> antes del acuse pero que
   * nos llegó después sigue siendo algo que nadie ha visto: si se comparara contra {@code
   * ocurrioEn}, este paquete desaparecería de la vista sin que nadie lo hubiera mirado.
   */
  @Test
  void un_evento_viejo_que_llego_tarde_tambien_vuelve_a_la_bandeja() {
    Envio envio = sembrarEnvioConEvento(EstadoEnvio.RETENIDO, DESPACHO, DESPACHO.plusSeconds(60));
    acusarGuia.ejecutar(new AcusarRevisionDeGuiaComando("NN-1", "admin:7", null));

    envio.registrarEvento(
        "NN-1",
        evento(EstadoEnvio.EXCEPCION, AHORA.minusSeconds(86_400), AHORA.plusSeconds(60), "ev-2"));
    envios.guardar(envio);

    assertEquals(1, bandeja.ejecutar(50).guias().size());
  }

  @Test
  void una_guia_que_no_existe_no_se_puede_acusar() {
    assertThrows(
        GuiaNoEncontradaException.class,
        () -> acusarGuia.ejecutar(new AcusarRevisionDeGuiaComando("NO-EXISTE", "admin:7", null)));
  }

  @Test
  void una_emision_indeterminada_esta_en_la_bandeja_con_su_tarifa() {
    Pedido pedido = sembrarPedido();
    EmisionDeGuia emision =
        EmisionDeGuia.solicitar(pedido.id(), "Coordinadora", "tarifa-1", "admin:7", DESPACHO);
    emision.indeterminada("la llamada no terminó", DESPACHO.plusSeconds(30));
    emisiones.guardar(emision);

    BandejaDeRevision resultado = bandeja.ejecutar(50);

    assertEquals(1, resultado.emisiones().size());
    EmisionEnRevision enRevision = resultado.emisiones().get(0);
    assertEquals(EstadoEmision.INDETERMINADA, enRevision.estado());
    assertEquals("tarifa-1", enRevision.idTarifa());
    assertEquals(pedido.numeroPedido().valor(), enRevision.numeroPedido());
  }

  @Test
  void un_acuse_saca_la_emision_de_la_bandeja_y_no_la_resuelve() {
    Pedido pedido = sembrarPedido();
    EmisionDeGuia emision =
        EmisionDeGuia.solicitar(pedido.id(), "Coordinadora", "tarifa-1", "admin:7", DESPACHO);
    emision.indeterminada("la llamada no terminó", DESPACHO.plusSeconds(30));
    emisiones.guardar(emision);

    AcuseDeRevision acuse =
        acusarEmision.ejecutar(
            new AcusarRevisionDeEmisionComando(emision.id(), "admin:7", "No hubo cobro."));

    assertTrue(bandeja.ejecutar(50).emisiones().isEmpty());
    assertEquals(emision.id(), acuse.referencia());
    // Sigue abierta: acusarla deja rastro, no desbloquea el pedido. Eso es plata y es otra puerta.
    assertEquals(
        EstadoEmision.INDETERMINADA, emisiones.buscarPorId(emision.id()).orElseThrow().estado());
    assertTrue(emisiones.buscarPorId(emision.id()).orElseThrow().estado().abierta());
  }

  @Test
  void una_emision_sana_no_se_puede_acusar() {
    Pedido pedido = sembrarPedido();
    EmisionDeGuia emision =
        EmisionDeGuia.solicitar(pedido.id(), "Envía", "tarifa-1", "admin:7", DESPACHO);
    emision.aceptada(List.of("env-1"), DESPACHO);
    emision.resolver(EstadoEmision.EMITIDA, null, DESPACHO.plusSeconds(60));
    emisiones.guardar(emision);

    assertThrows(
        AcuseNoAplicableException.class,
        () ->
            acusarEmision.ejecutar(
                new AcusarRevisionDeEmisionComando(emision.id(), "admin:7", null)));
    assertTrue(acuses.guardados().isEmpty());
  }

  @Test
  void sin_nada_quieto_la_bandeja_esta_vacia() {
    sembrarEnvioConEvento(EstadoEnvio.EN_TRANSITO, DESPACHO, DESPACHO.plusSeconds(60));

    BandejaDeRevision resultado = bandeja.ejecutar(50);

    assertTrue(resultado.vacia());
    assertFalse(resultado.guias().size() > 0);
  }
}
