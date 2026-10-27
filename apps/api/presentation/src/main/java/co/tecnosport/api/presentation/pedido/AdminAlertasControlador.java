package co.tecnosport.api.presentation.pedido;

import co.tecnosport.api.application.pago.ListarPagosSinTransaccion;
import co.tecnosport.api.application.pago.MarcarPagoSinTransaccionRevisado;
import co.tecnosport.api.application.pago.PagoSinTransaccion;
import co.tecnosport.api.application.pago.PagosSinTransaccion;
import co.tecnosport.api.application.pedido.ListarPedidosDetenidosEnPreparacion;
import co.tecnosport.api.application.pedido.PedidoDetenido;
import co.tecnosport.api.application.pedido.PedidosDetenidosEnPreparacion;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.presentation.compartido.dto.DineroRespuesta;
import co.tecnosport.api.presentation.pedido.dto.PagoSinTransaccionRespuesta;
import co.tecnosport.api.presentation.pedido.dto.PagosSinTransaccionRespuesta;
import co.tecnosport.api.presentation.pedido.dto.PedidoDetenidoRespuesta;
import co.tecnosport.api.presentation.pedido.dto.PedidosDetenidosRespuesta;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Las alertas del panel de pedidos: lo que ningún proceso automático resuelve y alguien tiene que
 * mirar. Bajo {@code /api/v1/admin/**}, protegido por rol ADMIN en {@code ConfiguracionSeguridad}.
 */
@RestController
@RequestMapping("/api/v1/admin/alertas")
public class AdminAlertasControlador {

  private final ListarPagosSinTransaccion listarPagosSinTransaccion;
  private final MarcarPagoSinTransaccionRevisado marcarRevisado;
  private final ListarPedidosDetenidosEnPreparacion listarDetenidosEnPreparacion;
  private final TransactionTemplate transaccion;

  public AdminAlertasControlador(
      ListarPagosSinTransaccion listarPagosSinTransaccion,
      MarcarPagoSinTransaccionRevisado marcarRevisado,
      ListarPedidosDetenidosEnPreparacion listarDetenidosEnPreparacion,
      PlatformTransactionManager transactionManager) {
    this.listarPagosSinTransaccion = Objects.requireNonNull(listarPagosSinTransaccion);
    this.marcarRevisado = Objects.requireNonNull(marcarRevisado);
    this.listarDetenidosEnPreparacion = Objects.requireNonNull(listarDetenidosEnPreparacion);
    this.transaccion = new TransactionTemplate(Objects.requireNonNull(transactionManager));
  }

  @GetMapping("/pagos-sin-transaccion")
  public PagosSinTransaccionRespuesta pagosSinTransaccion() {
    PagosSinTransaccion alerta = listarPagosSinTransaccion.ejecutar();
    return new PagosSinTransaccionRespuesta(
        alerta.umbral().toHours(),
        alerta.pagos().stream().map(AdminAlertasControlador::aRespuesta).toList());
  }

  @PostMapping("/pagos-sin-transaccion/{pagoId}/revision")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void marcarRevisado(@PathVariable UUID pagoId) {
    String actor = "admin:" + SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    transaccion.executeWithoutResult(estado -> marcarRevisado.ejecutar(pagoId, actor));
  }

  @GetMapping("/pedidos-en-preparacion")
  public PedidosDetenidosRespuesta pedidosDetenidosEnPreparacion() {
    PedidosDetenidosEnPreparacion alerta = listarDetenidosEnPreparacion.ejecutar();
    return new PedidosDetenidosRespuesta(
        alerta.umbral().toDays(),
        alerta.pedidos().stream().map(AdminAlertasControlador::aRespuesta).toList());
  }

  private static PedidoDetenidoRespuesta aRespuesta(PedidoDetenido item) {
    return new PedidoDetenidoRespuesta(
        item.pedido().id(),
        item.pedido().numeroPedido().valor(),
        item.pedido().correo().valor(),
        item.pedido().tipoEntrega().name(),
        item.pedido().metodoPago().name(),
        item.desde());
  }

  private static PagoSinTransaccionRespuesta aRespuesta(PagoSinTransaccion item) {
    return new PagoSinTransaccionRespuesta(
        item.pago().id(),
        item.pago().referencia().valor(),
        new DineroRespuesta(item.pago().monto().valor().longValueExact(), Dinero.MONEDA),
        item.pago().creadoEn(),
        item.pedido().id(),
        item.pedido().numeroPedido().valor(),
        item.pedido().estado().name(),
        item.pedido().correo().valor());
  }
}
