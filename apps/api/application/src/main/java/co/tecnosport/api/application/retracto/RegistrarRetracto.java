package co.tecnosport.api.application.retracto;

import co.tecnosport.api.application.compartido.CorreoNoEnviadoException;
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
   * El acuse va dentro de la misma transacción que abre el controlador, y un correo que no sale
   * <b>no</b> deshace la solicitud. Durante cuatro fases este comentario prometía lo contrario —"si
   * el correo falla, tampoco se guarda"— apoyándose en una garantía que el adaptador no daba: se
   * tragaba el fallo. Ahora el adaptador lanza y la decisión se toma aquí, escrita, y es la
   * contraria a la que aquel comentario prometía: <b>quien se retractó dentro de los cinco días
   * hábiles se retractó</b>, y perder esa constancia porque el servidor de correo estuviera caído
   * mueve la fecha del trámite, que es el dato que la Ley 1480 mide. Ver {@code adr/0044}.
   */
  private void enviarAcuse(Pedido pedido) {
    try {
      enviadorDeCorreo.enviar(
          pedido.correo(),
          textos.texto(TextoDeCorreo.RETRACTO_ACUSE_ASUNTO),
          textos.texto(TextoDeCorreo.RETRACTO_ACUSE_CUERPO, pedido.numeroPedido().valor()));
    } catch (CorreoNoEnviadoException registradoPorElAdaptador) {
      // Se traga: la operación pesa más que su aviso (adr/0044). Relanzar aquí revertiría la
      // transacción del controlador, y con ella la constancia — que es justo lo que no puede
      // faltar. La señal queda en el registro del adaptador; application no puede registrar nada,
      // no tiene slf4j en el classpath.
    }
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
