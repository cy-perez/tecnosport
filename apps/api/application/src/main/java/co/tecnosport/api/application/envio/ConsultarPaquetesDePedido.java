package co.tecnosport.api.application.envio;

import co.tecnosport.api.application.pedido.PedidoNoEncontradoException;
import co.tecnosport.api.application.pedido.RepositorioPedidos;
import co.tecnosport.api.domain.pedido.Pedido;
import co.tecnosport.api.domain.pedido.TipoEntrega;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Los paquetes de un pedido tal como hay que escribirlos en el formulario de la plataforma cuando
 * la guía se crea a mano ({@code adr/0071}): peso, medidas, valor declarado y contenido de cada
 * uno.
 *
 * <p>Existe porque hacerlo de cabeza sale mal justo donde más cuesta. En contraentrega el valor
 * declarado de <em>cada</em> paquete lleva repartido el flete, y la plataforma cobra en la puerta
 * la suma de lo declarado: escribir "el total del pedido" en cada uno de dos paquetes cobra el
 * doble, y el comprador rechaza el pedido. Y el peso de la bolsa es una suma de promedios que nadie
 * tiene por qué recalcular.
 *
 * <p>Son los mismos bultos que mandaría la emisión por API, armados por el mismo método ({@code
 * ArmadorDeBultos.armarParaDespachar}), así que lo que el panel enseña es lo que el sistema haría.
 * Se arman con las referencias <strong>de hoy</strong>: si alguien cambió un promedio después de la
 * compra, el comprador pagó un flete cotizado con el viejo.
 */
public final class ConsultarPaquetesDePedido {

  private final RepositorioPedidos repositorioPedidos;
  private final ArmadorDeBultos armador;

  public ConsultarPaquetesDePedido(RepositorioPedidos repositorioPedidos, ArmadorDeBultos armador) {
    this.repositorioPedidos = Objects.requireNonNull(repositorioPedidos);
    this.armador = Objects.requireNonNull(armador);
  }

  public PaquetesDePedido ejecutar(UUID pedidoId) {
    Objects.requireNonNull(pedidoId, "El pedido no puede ser nulo.");
    Pedido pedido =
        repositorioPedidos
            .buscarPorId(pedidoId)
            .orElseThrow(() -> new PedidoNoEncontradoException(pedidoId));
    if (pedido.tipoEntrega() != TipoEntrega.ENVIO_A_DOMICILIO) {
      throw new EmisionNoAplicableException(pedido.id(), "el pedido se recoge en el punto");
    }
    return new PaquetesDePedido(
        armador.armarParaDespachar(pedido), ArmadorDeBultos.llevaRecaudo(pedido));
  }

  /** Los paquetes, en el orden en que se crean, y si su valor declarado incluye el recaudo. */
  public record PaquetesDePedido(List<BultoDespachable> paquetes, boolean conRecaudo) {

    public PaquetesDePedido {
      paquetes = List.copyOf(paquetes);
    }
  }
}
