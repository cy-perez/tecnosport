package co.tecnosport.api.application.pedido;

import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.compartido.CorreoElectronicoInvalidoException;
import co.tecnosport.api.domain.pedido.Pedido;
import java.util.Objects;

/**
 * Seguimiento sin sesión ({@code POST /pedidos/{id}/seguimiento}, docs/03-api.md), adonde llevan
 * los enlaces de los correos: el comprobante, el despacho y, desde ADR-0088, el que pide el
 * formulario de "Estado del pedido" ({@code SolicitarEnlaceDeSeguimiento}). El "token" es el correo
 * mismo, que junto con el id del pedido (un UUID, no adivinable) es la prueba de que quien consulta
 * es quien hizo el pedido. Es el único camino por el que se ve un pedido sin sesión.
 *
 * <p>Un correo que no coincide se trata exactamente igual que un id inexistente — {@link
 * PedidoNoEncontradoException} en los dos casos, con el mismo mensaje— para no filtrar si el id
 * existe a quien no conoce el correo real.
 */
public final class ConsultarSeguimientoPedido {

  private final RepositorioPedidos repositorioPedidos;

  public ConsultarSeguimientoPedido(RepositorioPedidos repositorioPedidos) {
    this.repositorioPedidos = Objects.requireNonNull(repositorioPedidos);
  }

  public Pedido ejecutar(ConsultarSeguimientoPedidoComando comando) {
    Objects.requireNonNull(comando, "El comando no puede ser nulo.");
    Pedido pedido =
        repositorioPedidos
            .buscarPorId(comando.pedidoId())
            .orElseThrow(() -> new PedidoNoEncontradoException(comando.pedidoId()));
    // Se normaliza con el objeto de valor que escribe el dato, no con una copia de su línea: eran
    // tres implementaciones de lo mismo y habían divergido en el locale, que es lo que decide si
    // una `I` baja a `i` o a `ı`. Ver el javadoc de `CorreoElectronico`.
    if (!pedido.correo().valor().equals(normalizarCorreo(comando.correo()))) {
      throw new PedidoNoEncontradoException(comando.pedidoId());
    }
    return pedido;
  }

  /**
   * Un correo con formato imposible no coincide con ninguno guardado: se trata como el que no es.
   */
  private static String normalizarCorreo(String crudo) {
    try {
      return new CorreoElectronico(crudo).valor();
    } catch (CorreoElectronicoInvalidoException excepcion) {
      return "";
    }
  }
}
