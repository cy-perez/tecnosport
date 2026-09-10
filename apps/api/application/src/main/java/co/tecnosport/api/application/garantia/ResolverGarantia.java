package co.tecnosport.api.application.garantia;

import co.tecnosport.api.application.atencion.RepositorioSolicitudesAtencion;
import co.tecnosport.api.application.atencion.ResponderSolicitud;
import co.tecnosport.api.application.atencion.ResponderSolicitudComando;
import co.tecnosport.api.application.atencion.SolicitudAtencionNoEncontradaException;
import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.pedido.PedidoNoEncontradoException;
import co.tecnosport.api.application.pedido.RepositorioPedidos;
import co.tecnosport.api.application.reintegro.MontoDeReintegroInvalidoException;
import co.tecnosport.api.application.reintegro.RepositorioReintegros;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.garantia.DesenlaceGarantia;
import co.tecnosport.api.domain.garantia.ReclamacionGarantia;
import co.tecnosport.api.domain.pedido.Pedido;
import co.tecnosport.api.domain.reintegro.MotivoReintegro;
import co.tecnosport.api.domain.reintegro.Reintegro;
import java.util.Objects;
import java.util.UUID;

/**
 * Cierra una reclamación de garantía con una de las tres salidas de la ley, y de paso responde la
 * solicitud de atención que la contiene.
 *
 * <p>Las dos cosas juntas y no en dos pasos, por la misma razón que al radicar: resolver la
 * garantía <b>es</b> responderle al comprador. Separarlas dejaría reclamaciones resueltas con su
 * plazo de respuesta corriendo para siempre en la bandeja, que es exactamente lo que no puede pasar
 * con un plazo legal.
 *
 * <p>Con desenlace {@code REINTEGRO} deja además la constancia del dinero devuelto —un {@code
 * Reintegro} con motivo {@code GARANTIA}, el mismo tipo que dejan los otros cuatro caminos— y el
 * agregado exige su id: una garantía cerrada "devolviendo el dinero" sin prueba de que salió es
 * justo lo que la ley pide poder demostrar.
 *
 * <p>La vigencia no bloquea nada, ni siquiera fuera de término: puede haber garantía del fabricante
 * por detrás o una decisión comercial, y quien decide es una persona con la vigencia delante.
 */
public final class ResolverGarantia {

  private final RepositorioReclamacionesGarantia repositorioReclamaciones;
  private final RepositorioSolicitudesAtencion repositorioSolicitudes;
  private final RepositorioPedidos repositorioPedidos;
  private final RepositorioReintegros repositorioReintegros;
  private final ResponderSolicitud responderSolicitud;
  private final Reloj reloj;

  public ResolverGarantia(
      RepositorioReclamacionesGarantia repositorioReclamaciones,
      RepositorioSolicitudesAtencion repositorioSolicitudes,
      RepositorioPedidos repositorioPedidos,
      RepositorioReintegros repositorioReintegros,
      ResponderSolicitud responderSolicitud,
      Reloj reloj) {
    this.repositorioReclamaciones = Objects.requireNonNull(repositorioReclamaciones);
    this.repositorioSolicitudes = Objects.requireNonNull(repositorioSolicitudes);
    this.repositorioPedidos = Objects.requireNonNull(repositorioPedidos);
    this.repositorioReintegros = Objects.requireNonNull(repositorioReintegros);
    this.responderSolicitud = Objects.requireNonNull(responderSolicitud);
    this.reloj = Objects.requireNonNull(reloj);
  }

  public ReclamacionGarantia ejecutar(ResolverGarantiaComando comando) {
    Objects.requireNonNull(comando, "El comando no puede ser nulo.");
    ReclamacionGarantia reclamacion =
        repositorioReclamaciones
            .buscarPorId(comando.reclamacionId())
            .orElseThrow(
                () -> new ReclamacionGarantiaNoEncontradaException(comando.reclamacionId()));
    if (repositorioSolicitudes.buscarPorId(reclamacion.solicitudId()).isEmpty()) {
      throw new SolicitudAtencionNoEncontradaException(reclamacion.solicitudId());
    }

    UUID reintegroId = null;
    if (comando.desenlace() == DesenlaceGarantia.REINTEGRO) {
      reintegroId = registrarReintegro(reclamacion, comando).id();
    }

    reclamacion.resolver(comando.desenlace(), reintegroId, reloj.ahora(), comando.actor());
    repositorioReclamaciones.guardar(reclamacion);

    responderSolicitud.ejecutar(
        new ResponderSolicitudComando(
            reclamacion.solicitudId(), comando.resumenParaElComprador(), comando.actor()));
    return reclamacion;
  }

  private Reintegro registrarReintegro(
      ReclamacionGarantia reclamacion, ResolverGarantiaComando comando) {
    Pedido pedido =
        repositorioPedidos
            .buscarPorId(reclamacion.pedidoId())
            .orElseThrow(() -> new PedidoNoEncontradoException(reclamacion.pedidoId()));
    Dinero monto = Dinero.deCop(comando.monto());
    if (monto.valor().compareTo(pedido.total().valor()) > 0) {
      throw new MontoDeReintegroInvalidoException(monto, pedido.total());
    }
    Reintegro reintegro =
        Reintegro.registrar(
            pedido.id(),
            MotivoReintegro.GARANTIA,
            reclamacion.id(),
            monto,
            comando.medio(),
            comando.comprobante(),
            reloj.ahora(),
            comando.actor());
    repositorioReintegros.guardar(reintegro);
    return reintegro;
  }
}
