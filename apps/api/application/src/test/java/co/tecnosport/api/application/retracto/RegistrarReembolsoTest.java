package co.tecnosport.api.application.retracto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.application.compartido.RelojFalso;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import co.tecnosport.api.domain.pedido.MetodoPago;
import co.tecnosport.api.domain.pedido.Pedido;
import co.tecnosport.api.domain.retracto.CalendarioHabil;
import co.tecnosport.api.domain.retracto.EstadoSolicitudRetracto;
import co.tecnosport.api.domain.retracto.MedioReembolso;
import co.tecnosport.api.domain.retracto.PlazoDeRetracto;
import co.tecnosport.api.domain.retracto.Reembolso;
import co.tecnosport.api.domain.retracto.SolicitudRetracto;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RegistrarReembolsoTest {

  private static final Instant ENTREGA =
      ZonedDateTime.of(2026, 9, 10, 15, 30, 0, 0, PlazoDeRetracto.ZONA).toInstant();
  private static final Instant REEMBOLSO =
      ZonedDateTime.of(2026, 9, 20, 10, 0, 0, 0, PlazoDeRetracto.ZONA).toInstant();

  private final RepositorioSolicitudesRetractoFalso solicitudes =
      new RepositorioSolicitudesRetractoFalso();
  private final RepositorioPedidosParaRetractoFalso pedidos =
      new RepositorioPedidosParaRetractoFalso();
  private final EnviadorDeCorreoFalso correos = new EnviadorDeCorreoFalso();

  private RegistrarReembolso casoDeUso() {
    return new RegistrarReembolso(solicitudes, pedidos, correos, new RelojFalso(REEMBOLSO));
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
            new RegistrarReembolsoComando(
                solicitud.id(),
                BigDecimal.valueOf(50_000),
                MedioReembolso.TRANSFERENCIA_BANCARIA,
                "TRF-9912",
                "admin:1"));

    assertEquals(EstadoSolicitudRetracto.REEMBOLSADA, solicitud.estado());
    Reembolso reembolso = solicitud.reembolso().orElseThrow();
    assertEquals(BigDecimal.valueOf(50_000), reembolso.monto().valor());
    assertEquals(REEMBOLSO, reembolso.registradoEn());
    assertEquals("TRF-9912", reembolso.comprobanteOpcional().orElseThrow());
  }

  @Test
  void noSeReembolsaAntesDeQueVuelvaElProducto() {
    SolicitudRetracto solicitud = radicada();

    assertThrows(
        ExcepcionDeDominio.class,
        () ->
            casoDeUso()
                .ejecutar(
                    new RegistrarReembolsoComando(
                        solicitud.id(),
                        BigDecimal.valueOf(50_000),
                        MedioReembolso.WOMPI,
                        null,
                        "admin:1")));
    assertTrue(solicitud.reembolso().isEmpty());
  }

  @Test
  void noSeDevuelveMasDeLoQueSePago() {
    SolicitudRetracto solicitud = conProductoRecibido();

    assertThrows(
        MontoDeReembolsoInvalidoException.class,
        () ->
            casoDeUso()
                .ejecutar(
                    new RegistrarReembolsoComando(
                        solicitud.id(),
                        BigDecimal.valueOf(50_001),
                        MedioReembolso.WOMPI,
                        null,
                        "admin:1")));
    assertEquals(EstadoSolicitudRetracto.PRODUCTO_RECIBIDO, solicitud.estado());
  }

  @Test
  void unReembolsoParcialSeAdmite() {
    // Puede haber un descuento pactado o una línea de varias: la ley pone un techo, no un valor
    // exacto.
    SolicitudRetracto solicitud = conProductoRecibido();

    casoDeUso()
        .ejecutar(
            new RegistrarReembolsoComando(
                solicitud.id(),
                BigDecimal.valueOf(30_000),
                MedioReembolso.EFECTIVO,
                null,
                "admin:1"));

    assertEquals(BigDecimal.valueOf(30_000), solicitud.reembolso().orElseThrow().monto().valor());
  }

  @Test
  void unReembolsoDeCeroNoEsUnReembolso() {
    SolicitudRetracto solicitud = conProductoRecibido();

    assertThrows(
        ExcepcionDeDominio.class,
        () ->
            casoDeUso()
                .ejecutar(
                    new RegistrarReembolsoComando(
                        solicitud.id(), BigDecimal.ZERO, MedioReembolso.WOMPI, null, "admin:1")));
  }

  @Test
  void noSeReembolsaDosVeces() {
    SolicitudRetracto solicitud = conProductoRecibido();
    RegistrarReembolso caso = casoDeUso();
    RegistrarReembolsoComando comando =
        new RegistrarReembolsoComando(
            solicitud.id(), BigDecimal.valueOf(50_000), MedioReembolso.WOMPI, null, "admin:1");
    caso.ejecutar(comando);

    assertThrows(ExcepcionDeDominio.class, () -> caso.ejecutar(comando));
  }

  @Test
  void unaSolicitudInexistenteFalla() {
    assertThrows(
        SolicitudRetractoNoEncontradaException.class,
        () ->
            casoDeUso()
                .ejecutar(
                    new RegistrarReembolsoComando(
                        UUID.randomUUID(),
                        BigDecimal.valueOf(1000),
                        MedioReembolso.WOMPI,
                        null,
                        "admin:1")));
  }

  @Test
  void avisaAlCompradorDeQueElDineroSalio() {
    SolicitudRetracto solicitud = conProductoRecibido();

    casoDeUso()
        .ejecutar(
            new RegistrarReembolsoComando(
                solicitud.id(),
                BigDecimal.valueOf(50_000),
                MedioReembolso.TRANSFERENCIA_BANCARIA,
                null,
                "admin:1"));

    assertEquals(1, correos.enviados().size());
    assertTrue(correos.enviados().get(0).cuerpoHtml().contains("50000"));
  }
}
