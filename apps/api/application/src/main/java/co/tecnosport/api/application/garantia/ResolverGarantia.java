package co.tecnosport.api.application.garantia;

import co.tecnosport.api.application.atencion.RepositorioSolicitudesAtencion;
import co.tecnosport.api.application.atencion.ResponderSolicitud;
import co.tecnosport.api.application.atencion.ResponderSolicitudComando;
import co.tecnosport.api.application.atencion.SolicitudAtencionNoEncontradaException;
import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.pedido.PedidoNoEncontradoException;
import co.tecnosport.api.application.pedido.RepositorioPedidos;
import co.tecnosport.api.application.reintegro.ReintegroRequeridoException;
import co.tecnosport.api.application.reintegro.RepositorioReintegros;
import co.tecnosport.api.application.reintegro.TopeDeReintegro;
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
 * <p>El monto lo acota {@link TopeDeReintegro}, que cuenta lo ya devuelto por este pedido y no solo
 * esta resolución. Sin eso, una garantía resuelta con reintegro sobre un pedido que ya se devolvió
 * por retracto pagaba el total dos veces, y cada pago era válido por separado.
 *
 * <p>La vigencia no bloquea nada, ni siquiera fuera de término: puede haber garantía del fabricante
 * por detrás o una decisión comercial, y quien decide es una persona con la vigencia delante.
 */
public final class ResolverGarantia {

  private final RepositorioReclamacionesGarantia repositorioReclamaciones;
  private final RepositorioSolicitudesAtencion repositorioSolicitudes;
  private final RepositorioPedidos repositorioPedidos;
  private final RepositorioReintegros repositorioReintegros;
  private final TopeDeReintegro tope;
  private final ResponderSolicitud responderSolicitud;
  private final Reloj reloj;

  public ResolverGarantia(
      RepositorioReclamacionesGarantia repositorioReclamaciones,
      RepositorioSolicitudesAtencion repositorioSolicitudes,
      RepositorioPedidos repositorioPedidos,
      RepositorioReintegros repositorioReintegros,
      TopeDeReintegro tope,
      ResponderSolicitud responderSolicitud,
      Reloj reloj) {
    this.repositorioReclamaciones = Objects.requireNonNull(repositorioReclamaciones);
    this.repositorioSolicitudes = Objects.requireNonNull(repositorioSolicitudes);
    this.repositorioPedidos = Objects.requireNonNull(repositorioPedidos);
    this.repositorioReintegros = Objects.requireNonNull(repositorioReintegros);
    this.tope = Objects.requireNonNull(tope);
    this.responderSolicitud = Objects.requireNonNull(responderSolicitud);
    this.reloj = Objects.requireNonNull(reloj);
  }

  public ReclamacionGarantia ejecutar(ResolverGarantiaComando comando) {
    Objects.requireNonNull(comando, "El comando no puede ser nulo.");
    exigirDatosDelReintegro(comando);
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

  /**
   * Antes de tocar nada: elegir {@code REINTEGRO} y no decir cuánto ni por dónde no es un error de
   * sistema, es un cuerpo incompleto. Sin esta guarda el monto en nulo llegaba hasta el constructor
   * de {@code Dinero} y salía un 500 con "ocurrió un error inesperado", que no le dice a quien
   * atiende qué le falta. Es la misma guarda que {@code CancelarPedido} tenía desde el principio.
   */
  private static void exigirDatosDelReintegro(ResolverGarantiaComando comando) {
    if (comando.desenlace() != DesenlaceGarantia.REINTEGRO) {
      return;
    }
    if (comando.monto() == null || comando.medio() == null) {
      throw ReintegroRequeridoException.porqueElDesenlaceDevuelveDinero(comando.desenlace().name());
    }
  }

  private Reintegro registrarReintegro(
      ReclamacionGarantia reclamacion, ResolverGarantiaComando comando) {
    Pedido pedido =
        repositorioPedidos
            .buscarPorId(reclamacion.pedidoId())
            .orElseThrow(() -> new PedidoNoEncontradoException(reclamacion.pedidoId()));
    Dinero monto = Dinero.deCop(comando.monto());
    tope.exigirQueQuepa(pedido.id(), pedido.total(), monto);
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
