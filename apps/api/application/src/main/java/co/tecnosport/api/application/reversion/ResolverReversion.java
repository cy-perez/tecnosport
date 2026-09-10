package co.tecnosport.api.application.reversion;

import co.tecnosport.api.application.atencion.ResponderSolicitud;
import co.tecnosport.api.application.atencion.ResponderSolicitudComando;
import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.pedido.PedidoNoEncontradoException;
import co.tecnosport.api.application.pedido.RepositorioPedidos;
import co.tecnosport.api.application.reintegro.MontoDeReintegroInvalidoException;
import co.tecnosport.api.application.reintegro.RepositorioReintegros;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.pedido.Pedido;
import co.tecnosport.api.domain.reintegro.MotivoReintegro;
import co.tecnosport.api.domain.reintegro.Reintegro;
import co.tecnosport.api.domain.reversion.DesenlaceReversion;
import co.tecnosport.api.domain.reversion.SolicitudReversion;
import java.util.Objects;
import java.util.UUID;

/**
 * Cierra una solicitud de reversión y responde la solicitud de atención que la contiene, igual que
 * en la garantía: resolverla <b>es</b> responderle al comprador, y separarlas dejaría el plazo de
 * respuesta corriendo para siempre en la bandeja.
 *
 * <p>Solo deja constancia de dinero cuando el dinero salió de aquí. Si revirtió el emisor, la plata
 * volvió por la red de pagos y este sistema no movió un peso: inventarle un {@code Reintegro} sería
 * registrar un pago que no hicimos, y descuadraría la única pregunta que la constancia existe para
 * responder.
 */
public final class ResolverReversion {

  private final RepositorioSolicitudesReversion repositorioReversiones;
  private final RepositorioPedidos repositorioPedidos;
  private final RepositorioReintegros repositorioReintegros;
  private final ResponderSolicitud responderSolicitud;
  private final Reloj reloj;

  public ResolverReversion(
      RepositorioSolicitudesReversion repositorioReversiones,
      RepositorioPedidos repositorioPedidos,
      RepositorioReintegros repositorioReintegros,
      ResponderSolicitud responderSolicitud,
      Reloj reloj) {
    this.repositorioReversiones = Objects.requireNonNull(repositorioReversiones);
    this.repositorioPedidos = Objects.requireNonNull(repositorioPedidos);
    this.repositorioReintegros = Objects.requireNonNull(repositorioReintegros);
    this.responderSolicitud = Objects.requireNonNull(responderSolicitud);
    this.reloj = Objects.requireNonNull(reloj);
  }

  public SolicitudReversion ejecutar(ResolverReversionComando comando) {
    Objects.requireNonNull(comando, "El comando no puede ser nulo.");
    SolicitudReversion reversion =
        repositorioReversiones
            .buscarPorId(comando.reversionId())
            .orElseThrow(() -> new SolicitudReversionNoEncontradaException(comando.reversionId()));

    UUID reintegroId = null;
    if (comando.desenlace() == DesenlaceReversion.REINTEGRADO_DIRECTAMENTE) {
      reintegroId = registrarReintegro(reversion, comando).id();
    }

    reversion.resolver(comando.desenlace(), reintegroId, reloj.ahora());
    repositorioReversiones.guardar(reversion);

    responderSolicitud.ejecutar(
        new ResponderSolicitudComando(
            reversion.solicitudId(), comando.resumenParaElComprador(), comando.actor()));
    return reversion;
  }

  private Reintegro registrarReintegro(
      SolicitudReversion reversion, ResolverReversionComando comando) {
    Pedido pedido =
        repositorioPedidos
            .buscarPorId(reversion.pedidoId())
            .orElseThrow(() -> new PedidoNoEncontradoException(reversion.pedidoId()));
    Dinero monto = Dinero.deCop(comando.monto());
    if (monto.valor().compareTo(pedido.total().valor()) > 0) {
      throw new MontoDeReintegroInvalidoException(monto, pedido.total());
    }
    Reintegro reintegro =
        Reintegro.registrar(
            pedido.id(),
            MotivoReintegro.REVERSION,
            reversion.id(),
            monto,
            comando.medio(),
            comando.comprobante(),
            reloj.ahora(),
            comando.actor());
    repositorioReintegros.guardar(reintegro);
    return reintegro;
  }
}
