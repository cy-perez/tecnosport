package co.tecnosport.api.application.pedido;

import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.inventario.RepositorioInventario;
import co.tecnosport.api.domain.pedido.EstadoPedido;
import co.tecnosport.api.domain.pedido.MedioDePagoEnPunto;
import co.tecnosport.api.domain.pedido.MetodoPago;
import co.tecnosport.api.domain.pedido.Pedido;
import co.tecnosport.api.domain.pedido.TipoEntrega;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

/**
 * Entrega en el punto un pedido {@code LISTO_PARA_RECOGER} a quien muestre su número (ADR-0094).
 * Queda {@code ENTREGADO} con la fecha de hoy, y de ella corren el retracto y la garantía.
 *
 * <p><b>El número se compara aquí, no en la pantalla.</b> Es lo que el negocio decidió pedir para
 * saber que la mercancía va a quien la compró, y si solo lo mirara el panel bastaría un clic para
 * saltárselo. Se compara sin mayúsculas ni espacios alrededor: se dicta en voz alta o se lee de un
 * teléfono.
 *
 * <p>En {@code PAGO_EN_PUNTO} el dinero entra en este acto: se registra si fue en efectivo o por
 * transferencia y se confirman las reservas, que nadie confirmó antes porque no hubo pago antes —lo
 * mismo que hace {@code MarcarEntregado} con la contraentrega—. Un pedido pagado en línea ya las
 * confirmó al entrar el dinero, y un medio de pago en él sería un dato falso: se rechaza.
 */
public final class EntregarEnPunto {

  private final RepositorioPedidos repositorioPedidos;
  private final RepositorioInventario repositorioInventario;
  private final Reloj reloj;

  public EntregarEnPunto(
      RepositorioPedidos repositorioPedidos,
      RepositorioInventario repositorioInventario,
      Reloj reloj) {
    this.repositorioPedidos = Objects.requireNonNull(repositorioPedidos);
    this.repositorioInventario = Objects.requireNonNull(repositorioInventario);
    this.reloj = Objects.requireNonNull(reloj);
  }

  public ResultadoEntrega ejecutar(EntregarEnPuntoComando comando) {
    Objects.requireNonNull(comando, "El comando no puede ser nulo.");
    Pedido pedido =
        repositorioPedidos
            .buscarPorIdParaModificar(comando.pedidoId())
            .orElseThrow(() -> new PedidoNoEncontradoException(comando.pedidoId()));
    if (pedido.tipoEntrega() != TipoEntrega.RETIRO_EN_PUNTO) {
      throw new EntregaNoAplicaException(pedido.id(), "va a domicilio y no se recoge en el punto");
    }
    if (!coincide(comando.numeroPresentado(), pedido)) {
      throw new NumeroDePedidoNoCoincideException();
    }
    boolean pagaAlRecoger = pedido.metodoPago() == MetodoPago.PAGO_EN_PUNTO;
    if (pagaAlRecoger && comando.medioDePago() == null) {
      throw new IllegalArgumentException("Falta cómo pagó en el punto: efectivo o transferencia.");
    }
    if (!pagaAlRecoger && comando.medioDePago() != null) {
      throw new IllegalArgumentException(
          "Este pedido ya se pagó: no lleva medio de pago en el punto.");
    }
    Instant ahora = reloj.ahora();
    pedido.transicionar(EstadoPedido.ENTREGADO, comando.actor(), motivo(comando), ahora);
    boolean inventarioConfirmado = true;
    if (pagaAlRecoger) {
      inventarioConfirmado =
          ConfirmarReservasDeLineas.confirmar(pedido.lineas(), ahora, repositorioInventario);
    }
    repositorioPedidos.guardar(pedido);
    return new ResultadoEntrega(pedido, inventarioConfirmado);
  }

  private static boolean coincide(String presentado, Pedido pedido) {
    return presentado != null
        && presentado
            .strip()
            .toUpperCase(Locale.ROOT)
            .equals(pedido.numeroPedido().valor().toUpperCase(Locale.ROOT));
  }

  private static String motivo(EntregarEnPuntoComando comando) {
    if (comando.medioDePago() == null) {
      return "entregado en el punto";
    }
    return comando.medioDePago() == MedioDePagoEnPunto.EFECTIVO
        ? "entregado en el punto; pagó en efectivo"
        : "entregado en el punto; pagó por transferencia";
  }
}
