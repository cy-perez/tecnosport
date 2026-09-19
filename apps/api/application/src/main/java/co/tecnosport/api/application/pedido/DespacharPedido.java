package co.tecnosport.api.application.pedido;

import co.tecnosport.api.application.compartido.CorreoNoEnviadoException;
import co.tecnosport.api.application.compartido.EnviadorDeCorreo;
import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.compartido.TextoDeCorreo;
import co.tecnosport.api.application.compartido.TextosDeCorreo;
import co.tecnosport.api.application.envio.RepositorioEnvios;
import co.tecnosport.api.domain.envio.Envio;
import co.tecnosport.api.domain.envio.GuiaEnvio;
import co.tecnosport.api.domain.pedido.EstadoPedido;
import co.tecnosport.api.domain.pedido.Pedido;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Despacha un pedido en {@code EN_PREPARACION} (docs/11-pagos-y-envios.md): sus guías.
 *
 * <p><strong>Guías, en plural</strong> (adr/0031): ninguna transportadora colombiana admite
 * multipaquete, así que un pedido de dos variantes sale en dos paquetes, cada uno con su número y
 * su cobro. La transición se aplica primero — un {@code Envio} nunca queda huérfano de un despacho
 * que en realidad falló porque el pedido no estaba en {@code EN_PREPARACION} (un contraentrega sin
 * verificar todavía, o un segundo intento de despachar el mismo pedido).
 *
 * <p><b>Y se lo cuenta al comprador</b>, que hasta ahora era la mitad que faltaba: el despacho
 * funcionaba de punta a punta —panel, estado, guía— y la única persona que no se enteraba era la
 * que espera el paquete. Eso dejaba sin publicar el párrafo del numeral 8 de los términos
 * (docs/12-legales-de-envio.md §3), porque la publicidad obliga y no se promete lo que no se hace.
 *
 * <p>El correo <b>no</b> promete rastreo de eventos ni enlaza al sitio de la transportadora: este
 * sistema no consume esos eventos. Da los datos que sí tiene —transportadora, guía y el enlace a la
 * pantalla de estado— y ni uno más.
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
        "despachado con " + transportadoras(comando),
        ahora);
    Envio envio =
        Envio.crear(
            pedido.id(),
            comando.guias().stream()
                .map(
                    guia ->
                        guia.codigoTransportadora() == null
                            ? GuiaEnvio.crear(guia.transportadora(), guia.guia(), guia.costoEnvio())
                            : GuiaEnvio.emitida(
                                guia.transportadora(),
                                guia.codigoTransportadora(),
                                guia.guia(),
                                guia.costoEnvio(),
                                guia.urlEtiqueta()))
                .toList(),
            ahora);
    repositorioPedidos.guardar(pedido);
    repositorioEnvios.guardar(envio);
    avisarAlComprador(pedido, comando);
    return pedido;
  }

  /** Sin repetir: dos guías de la misma transportadora dicen su nombre una vez en el historial. */
  private static String transportadoras(DespacharPedidoComando comando) {
    return comando.guias().stream()
        .map(GuiaDespachada::transportadora)
        .distinct()
        .collect(Collectors.joining(", "));
  }

  /**
   * Después de guardar, no antes: mientras no exista una bandeja de salida, un correo que sale de
   * un despacho que luego no se comprometió es una promesa sin respaldo (ver el javadoc de {@link
   * EnviadorDeCorreo}, que deja escrito lo que hoy de verdad ocurre en los dos sentidos).
   */
  /**
   * Un correo que no sale <b>no</b> deshace el despacho, y aquí el motivo se ve sin esforzarse: la
   * guía ya está emitida y cobrada en Skydropx, y revertir la transacción no la desemite. Quedaría
   * un paquete que la transportadora recoge y entrega, de un pedido que el sistema sigue creyendo
   * en preparación. Ver {@code adr/0044}.
   */
  private void avisarAlComprador(Pedido pedido, DespacharPedidoComando comando) {
    List<GuiaDespachada> guias = comando.guias();
    String cuerpo =
        guias.size() == 1
            ? textos.texto(
                TextoDeCorreo.PEDIDO_DESPACHO_CUERPO,
                pedido.numeroPedido().valor(),
                guias.getFirst().transportadora(),
                guias.getFirst().guia(),
                enlaceDeEstado(pedido))
            : textos.texto(
                TextoDeCorreo.PEDIDO_DESPACHO_CUERPO_VARIAS,
                pedido.numeroPedido().valor(),
                String.valueOf(guias.size()),
                listadoDeGuias(guias),
                enlaceDeEstado(pedido));
    try {
      enviadorDeCorreo.enviar(
          pedido.correo(),
          textos.texto(TextoDeCorreo.PEDIDO_DESPACHO_ASUNTO, pedido.numeroPedido().valor()),
          cuerpo);
    } catch (CorreoNoEnviadoException registradoPorElAdaptador) {
      // Se traga: la operación pesa más que su aviso (adr/0044). Relanzar aquí revertiría la
      // transacción del controlador, y con ella la constancia — que es justo lo que no puede
      // faltar. La señal queda en el registro del adaptador; application no puede registrar nada,
      // no tiene slf4j en el classpath.
    }
  }

  /**
   * Texto plano y no una lista de HTML: los argumentos se escapan en el puerto —lo que protege de
   * que un nombre escrito en el panel se cuele como marcado— y un {@code <li>} llegaría a la
   * bandeja como letra, no como viñeta.
   *
   * <p>Que el correo diga cuántos paquetes son no es un adorno: quien recibe uno de dos y no lo
   * sabe, cree que le faltó media compra y escribe a atención.
   */
  private static String listadoDeGuias(List<GuiaDespachada> guias) {
    return guias.stream()
        .map(guia -> guia.transportadora() + " " + guia.guia())
        .collect(Collectors.joining(", "));
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
