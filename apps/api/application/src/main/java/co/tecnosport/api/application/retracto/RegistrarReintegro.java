package co.tecnosport.api.application.retracto;

import co.tecnosport.api.application.compartido.EnviadorDeCorreo;
import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.pedido.PedidoNoEncontradoException;
import co.tecnosport.api.application.pedido.RepositorioPedidos;
import co.tecnosport.api.application.reintegro.RepositorioReintegros;
import co.tecnosport.api.application.reintegro.TopeDeReintegro;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.pedido.Pedido;
import co.tecnosport.api.domain.reintegro.MotivoReintegro;
import co.tecnosport.api.domain.reintegro.Reintegro;
import co.tecnosport.api.domain.retracto.SolicitudRetracto;
import java.util.Objects;

/**
 * Anota que el dinero salió por un retracto y cierra la solicitud.
 *
 * <p>No mueve un peso, y eso es deliberado: de los tres métodos de pago del sitio, dos
 * —transferencia manual y contraentrega— se devuelven por fuera del sistema pase lo que pase, y
 * para el tercero no está verificado que la pasarela exponga la devolución por API. Un caso de uso
 * que pretendiera devolver automáticamente sería mentira en dos de cada tres pedidos. Lo que sí
 * hace falta —y es lo que la ley pide demostrar— es la constancia de cuándo salió, por dónde y
 * cuánto.
 *
 * <p>La constancia es un {@code Reintegro} con motivo {@code RETRACTO}, del mismo tipo que dejan
 * los otros cuatro caminos que devuelven dinero. La transición de la solicitud va <b>antes</b> de
 * guardar nada, mismo criterio que el resto del proyecto: un segundo intento se bloquea en la
 * máquina de estados y no llega a escribir una segunda constancia del mismo hecho. Los dos guardar
 * comparten la transacción que abre el controlador, así que o quedan ambos o no queda ninguno.
 *
 * <p>El monto lo acota {@link TopeDeReintegro}, que cuenta lo ya devuelto por este pedido y no solo
 * esta operación: el retracto es uno de los cinco caminos que devuelven dinero, y ninguno puede
 * pasarse del total por su cuenta.
 *
 * <p>El estado previo lo exige la máquina de estados: solo se reembolsa lo que ya volvió. Pagar
 * antes de recibir es una decisión comercial legítima, pero no se registra como retracto cumplido.
 */
public final class RegistrarReintegro {

  private final RepositorioSolicitudesRetracto repositorioSolicitudes;
  private final RepositorioPedidos repositorioPedidos;
  private final RepositorioReintegros repositorioReintegros;
  private final TopeDeReintegro tope;
  private final EnviadorDeCorreo enviadorDeCorreo;
  private final Reloj reloj;

  public RegistrarReintegro(
      RepositorioSolicitudesRetracto repositorioSolicitudes,
      RepositorioPedidos repositorioPedidos,
      RepositorioReintegros repositorioReintegros,
      TopeDeReintegro tope,
      EnviadorDeCorreo enviadorDeCorreo,
      Reloj reloj) {
    this.repositorioSolicitudes = Objects.requireNonNull(repositorioSolicitudes);
    this.repositorioPedidos = Objects.requireNonNull(repositorioPedidos);
    this.repositorioReintegros = Objects.requireNonNull(repositorioReintegros);
    this.tope = Objects.requireNonNull(tope);
    this.enviadorDeCorreo = Objects.requireNonNull(enviadorDeCorreo);
    this.reloj = Objects.requireNonNull(reloj);
  }

  public SolicitudRetracto ejecutar(RegistrarReintegroComando comando) {
    Objects.requireNonNull(comando, "El comando no puede ser nulo.");
    SolicitudRetracto solicitud =
        repositorioSolicitudes
            .buscarPorId(comando.solicitudId())
            .orElseThrow(() -> new SolicitudRetractoNoEncontradaException(comando.solicitudId()));
    Pedido pedido =
        repositorioPedidos
            .buscarPorId(solicitud.pedidoId())
            .orElseThrow(() -> new PedidoNoEncontradoException(solicitud.pedidoId()));

    // Antes de registrar nada: cerrar la solicitud la deja REEMBOLSADA, y con el dinero ya
    // devuelto el dominio no acepta anotar preferencias — anotarla ahí sería escribir el examen
    // viendo las respuestas.
    if (comando.medioPreferido() != null) {
      solicitud.anotarMedioPreferido(comando.medioPreferido());
    }

    Dinero monto = Dinero.deCop(comando.monto());
    // El tope va antes de tocar la solicitud, no después: noSeDevuelveMasDeLoQueSePago fija que
    // un monto inválido no la transicione, y con razón — quien reintenta con el monto corregido
    // tiene que encontrarla como la dejó. Cuesta que un doble clic de un reintegro por el total
    // lo rechace el tope y no la máquina de estados, con un mensaje que habla del pedido en vez
    // de la solicitud; es el precio correcto, porque las dos guardas bloquean y ninguna escribe.
    tope.exigirQueQuepa(pedido.id(), pedido.total(), monto);

    Reintegro reintegro =
        Reintegro.registrar(
            pedido.id(),
            MotivoReintegro.RETRACTO,
            solicitud.id(),
            monto,
            comando.medio(),
            comando.comprobante(),
            reloj.ahora(),
            comando.actor());

    solicitud.registrarReintegro(reintegro.id());

    repositorioReintegros.guardar(reintegro);
    repositorioSolicitudes.guardar(solicitud);
    enviarConstancia(pedido, monto);
    return solicitud;
  }

  /**
   * Mismo criterio que el acuse de {@code RegistrarRetracto}: si el correo falla, tampoco se guarda
   * la constancia. Aqui pesa todavia mas — este es el correo que le dice al comprador que su dinero
   * salio, y darlo por enviado sin que salga es justo lo que genera el reclamo que el registro
   * pretendia evitar.
   */
  private void enviarConstancia(Pedido pedido, Dinero monto) {
    enviadorDeCorreo.enviar(
        pedido.correo(),
        "Reintegramos el dinero de tu pedido — TecnoSport",
        "<p>Reintegramos "
            + monto.valor().toPlainString()
            + " "
            + Dinero.MONEDA
            + " del pedido "
            + pedido.numeroPedido().valor()
            + ".</p>"
            + "<p>Segun el medio, el dinero puede tardar en reflejarse en tu cuenta. Si pasados "
            + "unos dias no lo ves, escribenos y lo revisamos contigo.</p>");
  }
}
