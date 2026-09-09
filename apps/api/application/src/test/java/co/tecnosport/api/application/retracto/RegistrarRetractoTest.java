package co.tecnosport.api.application.retracto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.application.compartido.RelojFalso;
import co.tecnosport.api.application.pedido.PedidoNoEncontradoException;
import co.tecnosport.api.domain.pedido.MetodoPago;
import co.tecnosport.api.domain.pedido.Pedido;
import co.tecnosport.api.domain.retracto.CalendarioHabil;
import co.tecnosport.api.domain.retracto.EstadoSolicitudRetracto;
import co.tecnosport.api.domain.retracto.PlazoDeRetracto;
import co.tecnosport.api.domain.retracto.SolicitudRetracto;
import co.tecnosport.api.domain.retracto.VerdictoPlazo;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RegistrarRetractoTest {

  private static final Instant ENTREGA =
      ZonedDateTime.of(2026, 9, 10, 15, 30, 0, 0, PlazoDeRetracto.ZONA).toInstant();

  private final RepositorioSolicitudesRetractoFalso solicitudes =
      new RepositorioSolicitudesRetractoFalso();
  private final RepositorioPedidosParaRetractoFalso pedidos =
      new RepositorioPedidosParaRetractoFalso();
  private final EnviadorDeCorreoFalso correos = new EnviadorDeCorreoFalso();

  private static Instant enBogota(int mes, int dia) {
    return ZonedDateTime.of(2026, mes, dia, 10, 0, 0, 0, PlazoDeRetracto.ZONA).toInstant();
  }

  private RegistrarRetracto casoDeUso(Instant ahora, CalendarioHabil calendario) {
    return new RegistrarRetracto(solicitudes, pedidos, calendario, correos, new RelojFalso(ahora));
  }

  private Pedido pedidoEntregado() {
    Pedido pedido =
        PedidosDePrueba.entregado(
            MetodoPago.NEQUI, PedidosDePrueba.linea(UUID.randomUUID(), UUID.randomUUID()), ENTREGA);
    pedidos.sembrar(pedido);
    return pedido;
  }

  @Test
  void radicaConElVerdictoDelDiaCongelado() {
    Pedido pedido = pedidoEntregado();

    SolicitudRetracto solicitud =
        casoDeUso(enBogota(9, 14), CalendarioHabil.sinFestivosCargados())
            .ejecutar(new RegistrarRetractoComando(pedido.id(), null, "admin:1"));

    assertEquals(VerdictoPlazo.EN_PLAZO, solicitud.verdictoAlRadicar());
    assertEquals(EstadoSolicitudRetracto.RADICADA, solicitud.estado());
    assertEquals(1, solicitudes.todas().size());
  }

  @Test
  void unPedidoSinEntregarNoAdmiteRetracto() {
    // El plazo se cuenta desde la entrega: sin entrega el derecho ni empezó a correr.
    Pedido pedido =
        PedidosDePrueba.despachado(
            MetodoPago.NEQUI, PedidosDePrueba.linea(UUID.randomUUID(), UUID.randomUUID()), ENTREGA);
    pedidos.sembrar(pedido);

    assertThrows(
        PedidoSinEntregarException.class,
        () ->
            casoDeUso(enBogota(9, 14), CalendarioHabil.sinFestivosCargados())
                .ejecutar(new RegistrarRetractoComando(pedido.id(), null, "admin:1")));
    assertTrue(solicitudes.todas().isEmpty());
  }

  @Test
  void unPedidoInexistenteFalla() {
    assertThrows(
        PedidoNoEncontradoException.class,
        () ->
            casoDeUso(enBogota(9, 14), CalendarioHabil.sinFestivosCargados())
                .ejecutar(new RegistrarRetractoComando(UUID.randomUUID(), null, "admin:1")));
  }

  @Test
  void noSeRadicaDosVecesSobreElMismoPedido() {
    Pedido pedido = pedidoEntregado();
    RegistrarRetracto caso = casoDeUso(enBogota(9, 14), CalendarioHabil.sinFestivosCargados());
    caso.ejecutar(new RegistrarRetractoComando(pedido.id(), null, "admin:1"));

    assertThrows(
        RetractoYaRadicadoException.class,
        () -> caso.ejecutar(new RegistrarRetractoComando(pedido.id(), null, "admin:1")));
    assertEquals(1, solicitudes.todas().size());
  }

  @Test
  void trasUnRechazoSePuedeVolverARadicar() {
    // Rechazada no cierra la puerta: el comprador puede volver con más argumentos.
    Pedido pedido = pedidoEntregado();
    RegistrarRetracto caso = casoDeUso(enBogota(9, 14), CalendarioHabil.sinFestivosCargados());
    SolicitudRetracto primera =
        caso.ejecutar(new RegistrarRetractoComando(pedido.id(), null, "admin:1"));
    primera.transicionar(EstadoSolicitudRetracto.RECHAZADA);
    solicitudes.guardar(primera);

    caso.ejecutar(new RegistrarRetractoComando(pedido.id(), "trae la factura", "admin:1"));

    assertEquals(2, solicitudes.todas().size());
  }

  @Test
  void fueraDePlazoSeRadicaIgualPeroQuedaMarcado() {
    Pedido pedido = pedidoEntregado();

    SolicitudRetracto solicitud =
        casoDeUso(enBogota(10, 30), CalendarioHabil.con(Map.of(2026, Set.of())))
            .ejecutar(new RegistrarRetractoComando(pedido.id(), null, "admin:1"));

    assertEquals(VerdictoPlazo.VENCIDO, solicitud.verdictoAlRadicar());
    assertEquals(1, solicitudes.todas().size());
  }

  @Test
  void sinFestivosCargadosNoSeAfirmaQueVencio() {
    Pedido pedido = pedidoEntregado();

    SolicitudRetracto solicitud =
        casoDeUso(enBogota(10, 30), CalendarioHabil.sinFestivosCargados())
            .ejecutar(new RegistrarRetractoComando(pedido.id(), null, "admin:1"));

    assertEquals(VerdictoPlazo.INDETERMINADO, solicitud.verdictoAlRadicar());
  }

  @Test
  void avisaAlCompradorConElAcuseDeRecibo() {
    Pedido pedido = pedidoEntregado();

    casoDeUso(enBogota(9, 14), CalendarioHabil.sinFestivosCargados())
        .ejecutar(new RegistrarRetractoComando(pedido.id(), null, "admin:1"));

    assertEquals(1, correos.enviados().size());
    assertEquals("cliente@tecnosport.co", correos.enviados().get(0).destinatario().valor());
    // Los dos datos que el acuse tiene que llevar: quien paga el flete de vuelta y el plazo del
    // reintegro. Sin ellos el comprador no sabe que hacer con el producto.
    assertTrue(correos.enviados().get(0).cuerpoHtml().contains("articulo 47"));
    assertTrue(correos.enviados().get(0).cuerpoHtml().contains("quince (15) dias"));
  }

  @Test
  void siElCorreoFallaNoQuedaUnaSolicitudSinAcuse() {
    Pedido pedido = pedidoEntregado();
    correos.hazQueFalle();

    assertThrows(
        IllegalStateException.class,
        () ->
            casoDeUso(enBogota(9, 14), CalendarioHabil.sinFestivosCargados())
                .ejecutar(new RegistrarRetractoComando(pedido.id(), null, "admin:1")));
  }
}
