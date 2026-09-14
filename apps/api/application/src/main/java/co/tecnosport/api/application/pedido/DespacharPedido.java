package co.tecnosport.api.application.pedido;

import co.tecnosport.api.application.compartido.EnviadorDeCorreo;
import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.compartido.TextoDeCorreo;
import co.tecnosport.api.application.compartido.TextosDeCorreo;
import co.tecnosport.api.application.envio.RepositorioEnvios;
import co.tecnosport.api.domain.envio.Envio;
import co.tecnosport.api.domain.pedido.EstadoPedido;
import co.tecnosport.api.domain.pedido.Pedido;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Objects;

/**
 * Despacha un pedido en {@code EN_PREPARACION} (docs/11-pagos-y-envios.md): transportadora y guía.
 * La transición se aplica primero — un {@code Envio} nunca queda huérfano de un despacho que en
 * realidad falló porque el pedido no estaba en {@code EN_PREPARACION} (un contraentrega sin
 * verificar todavía, o un segundo intento de despachar el mismo pedido).
 *
 * <p><b>Y se lo cuenta al comprador</b>, que hasta ahora era la mitad que faltaba: el despacho
 * funcionaba de punta a punta —panel, estado, guía— y la única persona que no se enteraba era la
 * que espera el paquete. Eso dejaba sin publicar el párrafo del numeral 8 de los términos
 * (docs/12-legales-de-envio.md §3), porque la publicidad obliga y no se promete lo que no se hace.
 *
 * <p>El correo <b>no</b> promete rastreo de eventos ni enlaza al sitio de la transportadora: este
 * sistema no consume esos eventos. Da los tres datos que sí tiene —transportadora, guía y el enlace
 * a la pantalla de estado— y ni uno más.
 */
public final class DespacharPedido {

  private final RepositorioPedidos repositorioPedidos;
  private final RepositorioEnvios repositorioEnvios;
  private final EnviadorDeCorreo enviadorDeCorreo;
  private final TextosDeCorreo textos;
  private final Reloj reloj;
  private final String urlBaseEstado;

  public DespacharPedido(
      RepositorioPedidos repositorioPedidos,
      RepositorioEnvios repositorioEnvios,
      EnviadorDeCorreo enviadorDeCorreo,
      TextosDeCorreo textos,
      Reloj reloj,
      String urlBaseEstado) {
    this.repositorioPedidos = Objects.requireNonNull(repositorioPedidos);
    this.repositorioEnvios = Objects.requireNonNull(repositorioEnvios);
    this.enviadorDeCorreo = Objects.requireNonNull(enviadorDeCorreo);
    this.textos = Objects.requireNonNull(textos);
    this.reloj = Objects.requireNonNull(reloj);
    this.urlBaseEstado = Objects.requireNonNull(urlBaseEstado);
  }

  public Pedido ejecutar(DespacharPedidoComando comando) {
    Objects.requireNonNull(comando, "El comando no puede ser nulo.");
    Pedido pedido =
        repositorioPedidos
            .buscarPorId(comando.pedidoId())
            .orElseThrow(() -> new PedidoNoEncontradoException(comando.pedidoId()));
    Instant ahora = reloj.ahora();
    pedido.transicionar(
        EstadoPedido.DESPACHADO,
        comando.actor(),
        "despachado con " + comando.transportadora(),
        ahora);
    Envio envio =
        Envio.crear(
            pedido.id(), comando.transportadora(), comando.guia(), comando.costoEnvio(), ahora);
    repositorioPedidos.guardar(pedido);
    repositorioEnvios.guardar(envio);
    avisarAlComprador(pedido, comando);
    return pedido;
  }

  /**
   * Después de guardar, no antes: mientras no exista una bandeja de salida, un correo que sale de
   * un despacho que luego no se comprometió es una promesa sin respaldo (ver el javadoc de {@link
   * EnviadorDeCorreo}, que deja escrito lo que hoy de verdad ocurre en los dos sentidos).
   */
  private void avisarAlComprador(Pedido pedido, DespacharPedidoComando comando) {
    enviadorDeCorreo.enviar(
        pedido.correo(),
        textos.texto(TextoDeCorreo.PEDIDO_DESPACHO_ASUNTO, pedido.numeroPedido().valor()),
        textos.texto(
            TextoDeCorreo.PEDIDO_DESPACHO_CUERPO,
            pedido.numeroPedido().valor(),
            comando.transportadora(),
            comando.guia(),
            enlaceDeEstado(pedido)));
  }

  /**
   * El mismo camino que ya usa el retorno de Wompi: la pantalla de estado no pide sesión, se abre
   * con el id y el correo. Los dos van codificados — el correo lleva una arroba y puede llevar un
   * {@code +}, que en una cadena de consulta significa un espacio.
   */
  private String enlaceDeEstado(Pedido pedido) {
    return urlBaseEstado
        + "?pedidoId="
        + URLEncoder.encode(pedido.id().toString(), StandardCharsets.UTF_8)
        + "&correo="
        + URLEncoder.encode(pedido.correo().valor(), StandardCharsets.UTF_8);
  }
}
