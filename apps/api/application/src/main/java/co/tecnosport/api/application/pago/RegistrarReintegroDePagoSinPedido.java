package co.tecnosport.api.application.pago;

import co.tecnosport.api.application.compartido.CorreoNoEnviadoException;
import co.tecnosport.api.application.compartido.EnviadorDeCorreo;
import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.compartido.TextoDeCorreo;
import co.tecnosport.api.application.compartido.TextosDeCorreo;
import co.tecnosport.api.application.pedido.RepositorioPedidos;
import co.tecnosport.api.application.reintegro.RepositorioReintegros;
import co.tecnosport.api.domain.pago.Pago;
import co.tecnosport.api.domain.pedido.Pedido;
import co.tecnosport.api.domain.reintegro.MedioReintegro;
import co.tecnosport.api.domain.reintegro.MotivoReintegro;
import co.tecnosport.api.domain.reintegro.Reintegro;
import java.util.Objects;

/**
 * Deja la constancia de que un pago aprobado sin pedido que lo esperara se devolvió, y le avisa al
 * comprador.
 *
 * <p>El monto es el del pago, entero, y no lo teclea nadie. No pasa por {@code TopeDeReintegro}: no
 * fue dinero de la venta, y el tope cuenta lo que el pedido cobró. El origen es el propio pago, y
 * el índice único de {@code reintegro.origen_id} impide registrarlo dos veces aunque dos pestañas
 * lo intenten a la vez; la consulta previa da el error con nombre en el caso normal.
 *
 * <p>No mueve dinero, como ningún reintegro de este sistema: anota que alguien lo devolvió desde el
 * panel de la pasarela, o pidió la anulación del crédito a Sistecrédito.
 */
public final class RegistrarReintegroDePagoSinPedido {

  private final RepositorioPagos repositorioPagos;
  private final RepositorioPedidos repositorioPedidos;
  private final RepositorioReintegros repositorioReintegros;
  private final EnviadorDeCorreo enviadorDeCorreo;
  private final TextosDeCorreo textos;
  private final Reloj reloj;

  public RegistrarReintegroDePagoSinPedido(
      RepositorioPagos repositorioPagos,
      RepositorioPedidos repositorioPedidos,
      RepositorioReintegros repositorioReintegros,
      EnviadorDeCorreo enviadorDeCorreo,
      TextosDeCorreo textos,
      Reloj reloj) {
    this.repositorioPagos = Objects.requireNonNull(repositorioPagos);
    this.repositorioPedidos = Objects.requireNonNull(repositorioPedidos);
    this.repositorioReintegros = Objects.requireNonNull(repositorioReintegros);
    this.enviadorDeCorreo = Objects.requireNonNull(enviadorDeCorreo);
    this.textos = Objects.requireNonNull(textos);
    this.reloj = Objects.requireNonNull(reloj);
  }

  public Reintegro ejecutar(RegistrarReintegroDePagoSinPedidoComando comando) {
    Objects.requireNonNull(comando, "El comando no puede ser nulo.");
    Pago pago =
        repositorioPagos
            .buscarPorId(comando.pagoId())
            .orElseThrow(() -> new PagoNoEncontradoException(comando.pagoId().toString()));
    if (pago.sinPedidoQueLoEspereDesde().isEmpty()) {
      throw new PagoConPedidoQueLoEsperaException(pago.id());
    }
    if (repositorioReintegros.buscarPorOrigen(pago.id()).isPresent()) {
      throw new PagoSinPedidoYaDevueltoException(pago.id());
    }
    Reintegro reintegro =
        Reintegro.registrar(
            pago.pedidoId(),
            MotivoReintegro.PAGO_SIN_PEDIDO,
            pago.id(),
            pago.monto(),
            comando.medio(),
            comando.comprobante(),
            reloj.ahora(),
            comando.actor());
    repositorioReintegros.guardar(reintegro);
    repositorioPedidos.buscarPorId(pago.pedidoId()).ifPresent(p -> avisar(p, comando.medio()));
    return reintegro;
  }

  private void avisar(Pedido pedido, MedioReintegro medio) {
    TextoDeCorreo dinero =
        medio == MedioReintegro.SISTECREDITO
            ? TextoDeCorreo.PEDIDO_DEVUELTO_REINTEGRO_SISTECREDITO
            : TextoDeCorreo.PEDIDO_CANCELACION_CON_REINTEGRO;
    try {
      enviadorDeCorreo.enviar(
          pedido.correo(),
          textos.texto(TextoDeCorreo.PAGO_SIN_PEDIDO_ASUNTO, pedido.numeroPedido().valor()),
          textos.texto(TextoDeCorreo.PAGO_SIN_PEDIDO_CUERPO, pedido.numeroPedido().valor())
              + textos.texto(dinero));
    } catch (CorreoNoEnviadoException registradoPorElAdaptador) {
      // Mismo criterio que CancelarPedido (adr/0045): encolar se une a esta transacción.
    }
  }
}
