package co.tecnosport.api.application.reversion;

import co.tecnosport.api.application.atencion.ResponderSolicitud;
import co.tecnosport.api.application.atencion.ResponderSolicitudComando;
import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.pedido.PedidoNoEncontradoException;
import co.tecnosport.api.application.pedido.RepositorioPedidos;
import co.tecnosport.api.application.reintegro.ReintegroRequeridoException;
import co.tecnosport.api.application.reintegro.RepositorioReintegros;
import co.tecnosport.api.application.reintegro.TopeDeReintegro;
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
 *
 * <p>Cuando sí salió de aquí, el monto lo acota {@link TopeDeReintegro} contando lo ya devuelto por
 * este pedido: la reversión es uno de los cinco caminos, y el tope es del pedido, no de cada uno.
 */
public final class ResolverReversion {

  private final RepositorioSolicitudesReversion repositorioReversiones;
  private final RepositorioPedidos repositorioPedidos;
  private final RepositorioReintegros repositorioReintegros;
  private final TopeDeReintegro tope;
  private final ResponderSolicitud responderSolicitud;
  private final Reloj reloj;

  public ResolverReversion(
      RepositorioSolicitudesReversion repositorioReversiones,
      RepositorioPedidos repositorioPedidos,
      RepositorioReintegros repositorioReintegros,
      TopeDeReintegro tope,
      ResponderSolicitud responderSolicitud,
      Reloj reloj) {
    this.repositorioReversiones = Objects.requireNonNull(repositorioReversiones);
    this.repositorioPedidos = Objects.requireNonNull(repositorioPedidos);
    this.repositorioReintegros = Objects.requireNonNull(repositorioReintegros);
    this.tope = Objects.requireNonNull(tope);
    this.responderSolicitud = Objects.requireNonNull(responderSolicitud);
    this.reloj = Objects.requireNonNull(reloj);
  }

  public SolicitudReversion ejecutar(ResolverReversionComando comando) {
    Objects.requireNonNull(comando, "El comando no puede ser nulo.");
    exigirDatosDelReintegro(comando);
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

  /**
   * Igual que en la garantía: el único desenlace en que el dinero sale de aquí exige decir cuánto y
   * por dónde. Si revirtió el emisor no hace falta nada de eso, porque este sistema no movió un
   * peso.
   */
  private static void exigirDatosDelReintegro(ResolverReversionComando comando) {
    if (comando.desenlace() != DesenlaceReversion.REINTEGRADO_DIRECTAMENTE) {
      return;
    }
    if (comando.monto() == null || comando.medio() == null) {
      throw ReintegroRequeridoException.porqueElDesenlaceDevuelveDinero(comando.desenlace().name());
    }
  }

  private Reintegro registrarReintegro(
      SolicitudReversion reversion, ResolverReversionComando comando) {
    Pedido pedido =
        repositorioPedidos
            .buscarPorId(reversion.pedidoId())
            .orElseThrow(() -> new PedidoNoEncontradoException(reversion.pedidoId()));
    Dinero monto = Dinero.deCop(comando.monto());
    tope.exigirQueQuepa(pedido.id(), pedido.total(), monto);
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
