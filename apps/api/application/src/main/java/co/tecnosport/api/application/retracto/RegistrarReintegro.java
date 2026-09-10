package co.tecnosport.api.application.retracto;

import co.tecnosport.api.application.compartido.EnviadorDeCorreo;
import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.compartido.TextoDeCorreo;
import co.tecnosport.api.application.compartido.TextosDeCorreo;
import co.tecnosport.api.application.pedido.PedidoNoEncontradoException;
import co.tecnosport.api.application.pedido.RepositorioPedidos;
import co.tecnosport.api.application.reintegro.ReintegroRequeridoException;
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
  private final TextosDeCorreo textos;
  private final Reloj reloj;

  public RegistrarReintegro(
      RepositorioSolicitudesRetracto repositorioSolicitudes,
      RepositorioPedidos repositorioPedidos,
      RepositorioReintegros repositorioReintegros,
      TopeDeReintegro tope,
      EnviadorDeCorreo enviadorDeCorreo,
      TextosDeCorreo textos,
      Reloj reloj) {
    this.repositorioSolicitudes = Objects.requireNonNull(repositorioSolicitudes);
    this.repositorioPedidos = Objects.requireNonNull(repositorioPedidos);
    this.repositorioReintegros = Objects.requireNonNull(repositorioReintegros);
    this.tope = Objects.requireNonNull(tope);
    this.enviadorDeCorreo = Objects.requireNonNull(enviadorDeCorreo);
    this.textos = Objects.requireNonNull(textos);
    this.reloj = Objects.requireNonNull(reloj);
  }

  public SolicitudRetracto ejecutar(RegistrarReintegroComando comando) {
    Objects.requireNonNull(comando, "El comando no puede ser nulo.");
    // El tercer camino del dinero, que se quedó sin la guarda que sí recibieron la garantía y la
    // reversión: un cuerpo sin monto o sin medio llegaba hasta el requireNonNull de Dinero y salía
    // como 500. El formulario del panel lo evita, pero el backend no asume que la web es su único
    // cliente.
    if (comando.monto() == null || comando.medio() == null) {
      throw ReintegroRequeridoException.porqueUnReintegroSiempreDevuelve();
    }
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
    // El tope va antes de <b>transicionar</b> la solicitud —no antes de tocarla: la preferencia de
    // arriba ya la tocó— porque noSeDevuelveMasDeLoQueSePago fija que un monto inválido no la
    // transicione, y con razón: quien reintenta con el monto corregido tiene que encontrarla como
    // la
    // dejó. Cuesta que un doble clic de un reintegro por el total lo rechace el tope y no la
    // máquina
    // de estados, con un mensaje que habla del pedido en vez de la solicitud; es el precio
    // correcto,
    // porque las dos guardas bloquean y ninguna escribe.
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
   * Mismo caso que el acuse de {@code RegistrarRetracto}, y aquí pesa más: este es el correo que le
   * dice al comprador que su dinero salió. La garantía que este comentario prometía —si el correo
   * falla, tampoco se guarda la constancia— <b>no existe</b>: el adaptador se traga el fallo. O sea
   * que hoy puede quedar un reintegro registrado que el comprador nunca supo, y la única señal es
   * el registro de error del adaptador. Ver {@link
   * co.tecnosport.api.application.compartido.EnviadorDeCorreo}.
   */
  private void enviarConstancia(Pedido pedido, Dinero monto) {
    enviadorDeCorreo.enviar(
        pedido.correo(),
        textos.texto(TextoDeCorreo.RETRACTO_REINTEGRO_ASUNTO),
        textos.texto(
            TextoDeCorreo.RETRACTO_REINTEGRO_CUERPO,
            monto.valor().toPlainString(),
            Dinero.MONEDA,
            pedido.numeroPedido().valor()));
  }
}
