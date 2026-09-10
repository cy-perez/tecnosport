package co.tecnosport.api.application.retracto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.application.compartido.RelojFalso;
import co.tecnosport.api.application.reintegro.MontoDeReintegroInvalidoException;
import co.tecnosport.api.domain.compartido.CalendarioHabil;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import co.tecnosport.api.domain.pedido.MetodoPago;
import co.tecnosport.api.domain.pedido.Pedido;
import co.tecnosport.api.domain.reintegro.MedioReintegro;
import co.tecnosport.api.domain.reintegro.MotivoReintegro;
import co.tecnosport.api.domain.reintegro.Reintegro;
import co.tecnosport.api.domain.retracto.EstadoSolicitudRetracto;
import co.tecnosport.api.domain.retracto.PlazoDeRetracto;
import co.tecnosport.api.domain.retracto.SolicitudRetracto;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RegistrarReintegroTest {

  private static final Instant ENTREGA =
      ZonedDateTime.of(2026, 9, 10, 15, 30, 0, 0, PlazoDeRetracto.ZONA).toInstant();
  private static final Instant REINTEGRO =
      ZonedDateTime.of(2026, 9, 20, 10, 0, 0, 0, PlazoDeRetracto.ZONA).toInstant();

  private final RepositorioSolicitudesRetractoFalso solicitudes =
      new RepositorioSolicitudesRetractoFalso();
  private final RepositorioPedidosParaRetractoFalso pedidos =
      new RepositorioPedidosParaRetractoFalso();
  private final EnviadorDeCorreoFalso correos = new EnviadorDeCorreoFalso();

  private final RepositorioReintegrosFalso reintegros = new RepositorioReintegrosFalso();

  private RegistrarReintegro casoDeUso() {
    return new RegistrarReintegro(
        solicitudes, pedidos, reintegros, correos, new RelojFalso(REINTEGRO));
  }

  private Reintegro elReintegroDe(SolicitudRetracto solicitud) {
    return reintegros.buscarPorId(solicitud.reintegroId().orElseThrow()).orElseThrow();
  }

  /** El pedido de prueba vale una línea de 50.000. */
  private SolicitudRetracto conProductoRecibido() {
    SolicitudRetracto solicitud = radicada();
    solicitud.recibirProducto(ENTREGA.plusSeconds(86_400));
    solicitudes.guardar(solicitud);
    return solicitud;
  }

  private SolicitudRetracto radicada() {
    Pedido pedido =
        PedidosDePrueba.entregado(
            MetodoPago.NEQUI, PedidosDePrueba.linea(UUID.randomUUID(), UUID.randomUUID()), ENTREGA);
    pedidos.sembrar(pedido);
    SolicitudRetracto solicitud =
        SolicitudRetracto.radicar(
            pedido.id(),
            ENTREGA,
            ENTREGA.plusSeconds(3600),
            "admin:1",
            null,
            CalendarioHabil.sinFestivosCargados());
    solicitudes.guardar(solicitud);
    return solicitud;
  }

  @Test
  void dejaLaConstanciaYCierraLaSolicitud() {
    SolicitudRetracto solicitud = conProductoRecibido();

    casoDeUso()
        .ejecutar(
            new RegistrarReintegroComando(
                solicitud.id(),
                BigDecimal.valueOf(50_000),
                MedioReintegro.TRANSFERENCIA_BANCARIA,
                "TRF-9912",
                "admin:1"));

    assertEquals(EstadoSolicitudRetracto.REEMBOLSADA, solicitud.estado());
    Reintegro reintegro = elReintegroDe(solicitud);
    assertEquals(BigDecimal.valueOf(50_000), reintegro.monto().valor());
    assertEquals(REINTEGRO, reintegro.registradoEn());
    assertEquals("TRF-9912", reintegro.comprobante().orElseThrow());
  }

  /**
   * La constancia sabe de qué obligación nació y a qué solicitud responde. Sin las dos cosas es
   * plata que salió sin explicación, y es lo único que sostiene la invariante ahora que el
   * reintegro vive fuera de la solicitud.
   */
  @Test
  void laConstanciaGuardaSuMotivoYLaSolicitudQueLaJustifica() {
    SolicitudRetracto solicitud = conProductoRecibido();

    casoDeUso()
        .ejecutar(
            new RegistrarReintegroComando(
                solicitud.id(), BigDecimal.valueOf(50_000), MedioReintegro.WOMPI, null, "admin:1"));

    Reintegro reintegro = elReintegroDe(solicitud);
    assertEquals(MotivoReintegro.RETRACTO, reintegro.motivo());
    assertEquals(solicitud.id(), reintegro.origenId());
    assertEquals(solicitud.pedidoId(), reintegro.pedidoId());
  }

  @Test
  void noSeReembolsaAntesDeQueVuelvaElProducto() {
    SolicitudRetracto solicitud = radicada();

    assertThrows(
        ExcepcionDeDominio.class,
        () ->
            casoDeUso()
                .ejecutar(
                    new RegistrarReintegroComando(
                        solicitud.id(),
                        BigDecimal.valueOf(50_000),
                        MedioReintegro.WOMPI,
                        null,
                        "admin:1")));
    assertTrue(solicitud.reintegroId().isEmpty());
    assertTrue(reintegros.guardados().isEmpty(), "no queda constancia de un reintegro que no fue");
  }

  @Test
  void noSeDevuelveMasDeLoQueSePago() {
    SolicitudRetracto solicitud = conProductoRecibido();

    assertThrows(
        MontoDeReintegroInvalidoException.class,
        () ->
            casoDeUso()
                .ejecutar(
                    new RegistrarReintegroComando(
                        solicitud.id(),
                        BigDecimal.valueOf(50_001),
                        MedioReintegro.WOMPI,
                        null,
                        "admin:1")));
    assertEquals(EstadoSolicitudRetracto.PRODUCTO_RECIBIDO, solicitud.estado());
  }

  @Test
  void unReintegroParcialSeAdmite() {
    // Puede haber un descuento pactado o una línea de varias: la ley pone un techo, no un valor
    // exacto.
    SolicitudRetracto solicitud = conProductoRecibido();

    casoDeUso()
        .ejecutar(
            new RegistrarReintegroComando(
                solicitud.id(),
                BigDecimal.valueOf(30_000),
                MedioReintegro.EFECTIVO,
                null,
                "admin:1"));

    assertEquals(BigDecimal.valueOf(30_000), elReintegroDe(solicitud).monto().valor());
  }

  @Test
  void unReintegroDeCeroNoEsUnReintegro() {
    SolicitudRetracto solicitud = conProductoRecibido();

    assertThrows(
        ExcepcionDeDominio.class,
        () ->
            casoDeUso()
                .ejecutar(
                    new RegistrarReintegroComando(
                        solicitud.id(), BigDecimal.ZERO, MedioReintegro.WOMPI, null, "admin:1")));
  }

  /**
   * Y no deja una segunda constancia por el camino: la transición va antes de guardar nada, así que
   * el doble clic muere en la máquina de estados. Sin esa segunda afirmación, una implementación
   * que guardara primero pasaría esta prueba escribiendo dos veces el mismo hecho.
   */
  @Test
  void noSeReintegraDosVeces() {
    SolicitudRetracto solicitud = conProductoRecibido();
    RegistrarReintegro caso = casoDeUso();
    RegistrarReintegroComando comando =
        new RegistrarReintegroComando(
            solicitud.id(), BigDecimal.valueOf(50_000), MedioReintegro.WOMPI, null, "admin:1");
    caso.ejecutar(comando);

    assertThrows(ExcepcionDeDominio.class, () -> caso.ejecutar(comando));
    assertEquals(1, reintegros.guardados().size(), "constancias del mismo hecho");
  }

  @Test
  void unaSolicitudInexistenteFalla() {
    assertThrows(
        SolicitudRetractoNoEncontradaException.class,
        () ->
            casoDeUso()
                .ejecutar(
                    new RegistrarReintegroComando(
                        UUID.randomUUID(),
                        BigDecimal.valueOf(1000),
                        MedioReintegro.WOMPI,
                        null,
                        "admin:1")));
  }

  @Test
  void avisaAlCompradorDeQueElDineroSalio() {
    SolicitudRetracto solicitud = conProductoRecibido();

    casoDeUso()
        .ejecutar(
            new RegistrarReintegroComando(
                solicitud.id(),
                BigDecimal.valueOf(50_000),
                MedioReintegro.TRANSFERENCIA_BANCARIA,
                null,
                "admin:1"));

    assertEquals(1, correos.enviados().size());
    assertTrue(correos.enviados().get(0).cuerpoHtml().contains("50000"));
  }
}
