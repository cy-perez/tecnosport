package co.tecnosport.api.application.pedido;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import co.tecnosport.api.application.compartido.RelojFalso;
import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.Sku;
import co.tecnosport.api.domain.pedido.Direccion;
import co.tecnosport.api.domain.pedido.EstadoPedido;
import co.tecnosport.api.domain.pedido.LineaPedido;
import co.tecnosport.api.domain.pedido.MetodoPago;
import co.tecnosport.api.domain.pedido.NumeroPedido;
import co.tecnosport.api.domain.pedido.Pedido;
import co.tecnosport.api.domain.pedido.TipoEntrega;
import co.tecnosport.api.domain.pedido.TransicionDeEstadoInvalidaException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VerificarContraentregaTest {

  private static final Instant AHORA = Instant.parse("2026-09-03T12:00:00Z");
  private static final Direccion DIRECCION_MEDELLIN =
      new Direccion("05", "Antioquia", "05001", "Medellín", "Cra. 26C #38B-31", "Casa azul");

  private RepositorioPedidosFalso pedidos;

  private VerificarContraentrega crear() {
    pedidos = new RepositorioPedidosFalso();
    return new VerificarContraentrega(pedidos, new RelojFalso(AHORA));
  }

  private Pedido pedidoConMetodo(MetodoPago metodoPago) {
    Pedido pedido =
        Pedido.crear(
            NumeroPedido.de(2026, 1),
            null,
            new CorreoElectronico("cliente@tecnosport.co"),
            List.of(
                new LineaPedido(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    new Sku("TS-CAM-AZ-M"),
                    "Camiseta running Dry-Fit",
                    1,
                    Dinero.deCop(50_000),
                    new BigDecimal("0.19"),
                    "https://cdn.tecnosport.co/img.webp",
                    UUID.randomUUID())),
            TipoEntrega.ENVIO_A_DOMICILIO,
            DIRECCION_MEDELLIN,
            metodoPago,
            "cliente@tecnosport.co",
            AHORA);
    pedidos.guardar(pedido);
    return pedido;
  }

  @Test
  void verificaUnPedidoContraentregaConfirmado() {
    VerificarContraentrega caso = crear();
    Pedido pedido = pedidoConMetodo(MetodoPago.CONTRAENTREGA);

    Pedido verificado =
        caso.ejecutar(
            new VerificarContraentregaComando(
                pedido.id(), "admin:test", "Verificado por WhatsApp"));

    assertEquals(EstadoPedido.EN_PREPARACION, verificado.estado());
    assertEquals(2, verificado.historial().size());
    assertEquals("Verificado por WhatsApp", verificado.historial().get(1).motivo());
    assertEquals("admin:test", verificado.historial().get(1).actor());
  }

  @Test
  void unPedidoInexistenteLanzaPedidoNoEncontrado() {
    VerificarContraentrega caso = crear();

    assertThrows(
        PedidoNoEncontradoException.class,
        () ->
            caso.ejecutar(
                new VerificarContraentregaComando(UUID.randomUUID(), "admin:test", "verificado")));
  }

  @Test
  void unPedidoQueNoEstaConfirmadoContraentregaNoSePuedeVerificar() {
    VerificarContraentrega caso = crear();
    Pedido pedido = pedidoConMetodo(MetodoPago.NEQUI);

    assertThrows(
        TransicionDeEstadoInvalidaException.class,
        () ->
            caso.ejecutar(
                new VerificarContraentregaComando(pedido.id(), "admin:test", "verificado")));
  }

  @Test
  void unMotivoVacioSeRechaza() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new VerificarContraentregaComando(UUID.randomUUID(), "admin:test", " "));
  }
}
