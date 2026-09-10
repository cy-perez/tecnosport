package co.tecnosport.api.application.retracto;

import co.tecnosport.api.application.compartido.EnviadorDeCorreo;
import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.compartido.TextoDeCorreo;
import co.tecnosport.api.application.compartido.TextosDeCorreo;
import co.tecnosport.api.application.pedido.PedidoNoEncontradoException;
import co.tecnosport.api.application.pedido.RepositorioPedidos;
import co.tecnosport.api.domain.compartido.CalendarioHabil;
import co.tecnosport.api.domain.pedido.Pedido;
import co.tecnosport.api.domain.retracto.EstadoSolicitudRetracto;
import co.tecnosport.api.domain.retracto.SolicitudRetracto;
import java.time.Instant;
import java.util.Objects;

/**
 * Deja constancia de un retracto que el comprador ejerció por correo o WhatsApp, que es el canal
 * que los términos publicados prometen. No lo radica el comprador: lo radica quien atiende, y por
 * eso el actor es siempre una persona del negocio.
 *
 * <p>No bloquea por plazo vencido. El veredicto se congela en la solicitud y decide una persona con
 * ese dato delante: puede haber un acuerdo comercial o una garantía por detrás, y un derecho del
 * consumidor no se cierra con una guarda de software. Antes había una segunda razón —sin los
 * festivos cargados el sistema no podía ni afirmar que había vencido— que dejó de aplicar con
 * {@code ADR-0024}: hoy se calculan, y el veredicto en producción siempre es {@code EN_PLAZO} o
 * {@code VENCIDO}.
 */
public final class RegistrarRetracto {

  private final RepositorioSolicitudesRetracto repositorioSolicitudes;
  private final RepositorioPedidos repositorioPedidos;
  private final CalendarioHabil calendario;
  private final EnviadorDeCorreo enviadorDeCorreo;
  private final TextosDeCorreo textos;
  private final Reloj reloj;

  public RegistrarRetracto(
      RepositorioSolicitudesRetracto repositorioSolicitudes,
      RepositorioPedidos repositorioPedidos,
      CalendarioHabil calendario,
      EnviadorDeCorreo enviadorDeCorreo,
      TextosDeCorreo textos,
      Reloj reloj) {
    this.repositorioSolicitudes = Objects.requireNonNull(repositorioSolicitudes);
    this.repositorioPedidos = Objects.requireNonNull(repositorioPedidos);
    this.calendario = Objects.requireNonNull(calendario);
    this.enviadorDeCorreo = Objects.requireNonNull(enviadorDeCorreo);
    this.textos = Objects.requireNonNull(textos);
    this.reloj = Objects.requireNonNull(reloj);
  }

  public SolicitudRetracto ejecutar(RegistrarRetractoComando comando) {
    Objects.requireNonNull(comando, "El comando no puede ser nulo.");
    Pedido pedido =
        repositorioPedidos
            .buscarPorId(comando.pedidoId())
            .orElseThrow(() -> new PedidoNoEncontradoException(comando.pedidoId()));
    Instant entregadoEn =
        pedido
            .fechaDeEntrega()
            .orElseThrow(() -> new PedidoSinEntregarException(comando.pedidoId()));
    if (tieneUnaEnCurso(comando)) {
      throw new RetractoYaRadicadoException(comando.pedidoId());
    }
    SolicitudRetracto solicitud =
        SolicitudRetracto.radicar(
            pedido.id(),
            entregadoEn,
            reloj.ahora(),
            comando.actor(),
            comando.motivo(),
            comando.medioPreferido(),
            calendario);
    repositorioSolicitudes.guardar(solicitud);
    enviarAcuse(pedido);
    return solicitud;
  }

  /**
   * El acuse va dentro de la misma transaccion que abre el controlador, igual que en {@code
   * RegistrarUsuario}: si el correo falla, la solicitud tampoco se guarda y quien atiende ve el
   * error y reintenta. Es a proposito. Guardar la constancia y callar el fallo dejaria al panel
   * diciendo "radicado" con un comprador que nunca recibio nada, y el acuse es parte de lo que
   * demuestra que el tramite arranco el dia que dice.
   */
  private void enviarAcuse(Pedido pedido) {
    enviadorDeCorreo.enviar(
        pedido.correo(),
        textos.texto(TextoDeCorreo.RETRACTO_ACUSE_ASUNTO),
        textos.texto(TextoDeCorreo.RETRACTO_ACUSE_CUERPO, pedido.numeroPedido().valor()));
  }

  /**
   * Una rechazada no cuenta: si se rechazó, el comprador puede volver con más argumentos y hay que
   * poder radicar de nuevo. Una reembolsada sí cuenta — ese pedido ya se devolvió y se pagó.
   */
  private boolean tieneUnaEnCurso(RegistrarRetractoComando comando) {
    return repositorioSolicitudes.buscarPorPedidoId(comando.pedidoId()).stream()
        .anyMatch(s -> s.estado() != EstadoSolicitudRetracto.RECHAZADA);
  }
}
