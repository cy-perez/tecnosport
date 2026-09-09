package co.tecnosport.api.application.retracto;

import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.pedido.PedidoNoEncontradoException;
import co.tecnosport.api.application.pedido.RepositorioPedidos;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.pedido.Pedido;
import co.tecnosport.api.domain.retracto.Reembolso;
import co.tecnosport.api.domain.retracto.SolicitudRetracto;
import java.util.Objects;

/**
 * Anota que el dinero salió y cierra la solicitud.
 *
 * <p>No mueve un peso, y eso es deliberado: de los tres métodos de pago del sitio, dos
 * —transferencia manual y contraentrega— se devuelven por fuera del sistema pase lo que pase, y
 * para el tercero no está verificado que la pasarela exponga la devolución por API. Un caso de uso
 * que pretendiera devolver automáticamente sería mentira en dos de cada tres pedidos. Lo que sí
 * hace falta —y es lo que la ley pide demostrar— es la constancia de cuándo salió, por dónde y
 * cuánto.
 *
 * <p>El estado previo lo exige la máquina de estados: solo se reembolsa lo que ya volvió. Pagar
 * antes de recibir es una decisión comercial legítima, pero no se registra como retracto cumplido.
 */
public final class RegistrarReembolso {

  private final RepositorioSolicitudesRetracto repositorioSolicitudes;
  private final RepositorioPedidos repositorioPedidos;
  private final Reloj reloj;

  public RegistrarReembolso(
      RepositorioSolicitudesRetracto repositorioSolicitudes,
      RepositorioPedidos repositorioPedidos,
      Reloj reloj) {
    this.repositorioSolicitudes = Objects.requireNonNull(repositorioSolicitudes);
    this.repositorioPedidos = Objects.requireNonNull(repositorioPedidos);
    this.reloj = Objects.requireNonNull(reloj);
  }

  public SolicitudRetracto ejecutar(RegistrarReembolsoComando comando) {
    Objects.requireNonNull(comando, "El comando no puede ser nulo.");
    SolicitudRetracto solicitud =
        repositorioSolicitudes
            .buscarPorId(comando.solicitudId())
            .orElseThrow(() -> new SolicitudRetractoNoEncontradaException(comando.solicitudId()));
    Pedido pedido =
        repositorioPedidos
            .buscarPorId(solicitud.pedidoId())
            .orElseThrow(() -> new PedidoNoEncontradoException(solicitud.pedidoId()));

    Dinero monto = Dinero.deCop(comando.monto());
    if (monto.valor().compareTo(pedido.total().valor()) > 0) {
      throw new MontoDeReembolsoInvalidoException(monto, pedido.total());
    }

    solicitud.reembolsar(
        new Reembolso(
            monto, comando.medio(), comando.comprobante(), reloj.ahora(), comando.actor()));
    repositorioSolicitudes.guardar(solicitud);
    return solicitud;
  }
}
