package co.tecnosport.api.domain.pedido;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EstadoPedidoTest {

  @Test
  void creadoPuedeIrAPagoPendienteOAContraentregaConfirmada() {
    assertTrue(EstadoPedido.CREADO.puedeTransicionarA(EstadoPedido.PAGO_PENDIENTE));
    assertTrue(EstadoPedido.CREADO.puedeTransicionarA(EstadoPedido.CONFIRMADO_CONTRAENTREGA));
  }

  @Test
  void entregadoNuncaVuelveAPagado() {
    assertFalse(EstadoPedido.ENTREGADO.puedeTransicionarA(EstadoPedido.PAGADO));
  }

  @Test
  void pagoFallidoPuedeReintentar() {
    assertTrue(EstadoPedido.PAGO_FALLIDO.puedeTransicionarA(EstadoPedido.PAGO_PENDIENTE));
  }

  @Test
  void entregadoSeBifurcaEntreDevueltoYRecaudoPendiente() {
    assertTrue(EstadoPedido.ENTREGADO.puedeTransicionarA(EstadoPedido.DEVUELTO));
    assertTrue(EstadoPedido.ENTREGADO.puedeTransicionarA(EstadoPedido.RECAUDO_PENDIENTE));
  }

  @Test
  void despachadoPuedeRechazarseEnLaEntrega() {
    assertTrue(EstadoPedido.DESPACHADO.puedeTransicionarA(EstadoPedido.RECHAZADO_EN_ENTREGA));
  }

  @Test
  void losEstadosTerminalesNoTienenSalida() {
    assertFalse(EstadoPedido.RECHAZADO_EN_ENTREGA.puedeTransicionarA(EstadoPedido.EN_PREPARACION));
    assertFalse(EstadoPedido.DEVUELTO.puedeTransicionarA(EstadoPedido.ENTREGADO));
    assertFalse(EstadoPedido.RECAUDO_CONCILIADO.puedeTransicionarA(EstadoPedido.RECAUDO_PENDIENTE));
  }

  @Test
  void unContraentregaYaRecaudadoTambienPuedeDevolverse() {
    // El retracto no distingue el método de pago (Ley 1480 de 2011, art. 47). Sin esta arista,
    // devolver solo era posible por el camino de pago en línea.
    assertTrue(EstadoPedido.RECAUDO_CONCILIADO.puedeTransicionarA(EstadoPedido.DEVUELTO));
  }

  @Test
  void devolverNoSaltaPasosDelCaminoDeContraentrega() {
    assertFalse(EstadoPedido.RECAUDO_PENDIENTE.puedeTransicionarA(EstadoPedido.DEVUELTO));
  }

  @Test
  void recaudoPendienteConciliaHaciaRecaudoConciliado() {
    assertTrue(EstadoPedido.RECAUDO_PENDIENTE.puedeTransicionarA(EstadoPedido.RECAUDO_CONCILIADO));
  }
}
