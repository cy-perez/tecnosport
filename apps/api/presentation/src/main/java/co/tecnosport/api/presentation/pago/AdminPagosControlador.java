package co.tecnosport.api.presentation.pago;

import co.tecnosport.api.application.pago.ListarPagosSinPedido;
import co.tecnosport.api.application.pago.PagoSinPedido;
import co.tecnosport.api.application.pago.RegistrarReintegroDePagoSinPedido;
import co.tecnosport.api.application.pago.RegistrarReintegroDePagoSinPedidoComando;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.reintegro.MedioReintegro;
import co.tecnosport.api.presentation.compartido.dto.DineroRespuesta;
import co.tecnosport.api.presentation.pago.dto.PagoSinPedidoRespuesta;
import co.tecnosport.api.presentation.pago.dto.RegistrarReintegroDePagoSinPedidoRequest;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * La bandeja de pagos aprobados sin un pedido que los esperara, y su salida: registrar que se
 * devolvieron. El monto nunca viene del cliente.
 */
@RestController
@RequestMapping("/api/v1/admin/pagos")
public class AdminPagosControlador {

  private final ListarPagosSinPedido listarPagosSinPedido;
  private final RegistrarReintegroDePagoSinPedido registrarReintegro;
  private final TransactionTemplate transaccion;

  public AdminPagosControlador(
      ListarPagosSinPedido listarPagosSinPedido,
      RegistrarReintegroDePagoSinPedido registrarReintegro,
      PlatformTransactionManager transactionManager) {
    this.listarPagosSinPedido = Objects.requireNonNull(listarPagosSinPedido);
    this.registrarReintegro = Objects.requireNonNull(registrarReintegro);
    this.transaccion = new TransactionTemplate(Objects.requireNonNull(transactionManager));
  }

  @GetMapping("/sin-pedido")
  public List<PagoSinPedidoRespuesta> listar() {
    return listarPagosSinPedido.ejecutar().stream().map(AdminPagosControlador::aRespuesta).toList();
  }

  @PostMapping("/{id}/reintegro")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void registrarReintegro(
      @PathVariable UUID id, @RequestBody RegistrarReintegroDePagoSinPedidoRequest cuerpo) {
    String actor = "admin:" + SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    transaccion.executeWithoutResult(
        estado ->
            registrarReintegro.ejecutar(
                new RegistrarReintegroDePagoSinPedidoComando(
                    id, MedioReintegro.valueOf(cuerpo.medio()), cuerpo.comprobante(), actor)));
  }

  private static PagoSinPedidoRespuesta aRespuesta(PagoSinPedido item) {
    return new PagoSinPedidoRespuesta(
        item.pago().id(),
        item.pago().referencia().valor(),
        item.pago().metodoPago().name(),
        new DineroRespuesta(item.pago().monto().valor().longValueExact(), Dinero.MONEDA),
        item.pago().sinPedidoQueLoEspereDesde().orElseThrow(),
        item.pedido().id(),
        item.pedido().numeroPedido().valor(),
        item.pedido().estado().name(),
        item.pedido().correo().valor());
  }
}
