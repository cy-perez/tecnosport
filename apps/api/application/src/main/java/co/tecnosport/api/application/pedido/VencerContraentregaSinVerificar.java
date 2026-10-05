package co.tecnosport.api.application.pedido;

import co.tecnosport.api.application.compartido.CorreoNoEnviadoException;
import co.tecnosport.api.application.compartido.EnviadorDeCorreo;
import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.compartido.TextoDeCorreo;
import co.tecnosport.api.application.compartido.TextosDeCorreo;
import co.tecnosport.api.application.inventario.RepositorioInventario;
import co.tecnosport.api.domain.inventario.ReservaNoEncontradaException;
import co.tecnosport.api.domain.inventario.ReservaYaProcesadaException;
import co.tecnosport.api.domain.pedido.EstadoPedido;
import co.tecnosport.api.domain.pedido.LineaPedido;
import co.tecnosport.api.domain.pedido.Pedido;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Cancela un pedido contraentrega que nadie verificó a tiempo y libera su mercancía.
 *
 * <p>La reserva de contraentrega no vence: dura hasta el despacho, porque no hay pago que esperar.
 * Y la verificación —una llamada o un WhatsApp antes de despachar— no tenía plazo. Juntas, dejaban
 * a cualquiera bloquear la existencia de un producto sin pagar nada: bastaba confirmar pedidos con
 * correos distintos. El plazo lo fijó el negocio el 4 de octubre de 2026 —48 horas, configurable—,
 * y se cuenta desde la creación del pedido.
 *
 * <p>No vence la reserva sino el <b>pedido</b>, y es a propósito: una reserva con vencimiento
 * también caducaría después de verificar, con el pedido ya en preparación. Cancelar el pedido no
 * verificado libera la reserva y deja intacta la del que sí se verificó.
 *
 * <p>Contraentrega no cobra antes de entregar, así que no hay reintegro. Se le avisa al comprador.
 */
public final class VencerContraentregaSinVerificar {

  private static final int MAXIMO_POR_CORRIDA = 50;

  private final RepositorioPedidos repositorioPedidos;
  private final RepositorioInventario repositorioInventario;
  private final EnviadorDeCorreo enviadorDeCorreo;
  private final TextosDeCorreo textos;
  private final Reloj reloj;
  private final Duration plazoParaVerificar;

  public VencerContraentregaSinVerificar(
      RepositorioPedidos repositorioPedidos,
      RepositorioInventario repositorioInventario,
      EnviadorDeCorreo enviadorDeCorreo,
      TextosDeCorreo textos,
      Reloj reloj,
      Duration plazoParaVerificar) {
    this.repositorioPedidos = Objects.requireNonNull(repositorioPedidos);
    this.repositorioInventario = Objects.requireNonNull(repositorioInventario);
    this.enviadorDeCorreo = Objects.requireNonNull(enviadorDeCorreo);
    this.textos = Objects.requireNonNull(textos);
    this.reloj = Objects.requireNonNull(reloj);
    this.plazoParaVerificar = Objects.requireNonNull(plazoParaVerificar);
    if (plazoParaVerificar.isNegative() || plazoParaVerificar.isZero()) {
      throw new IllegalArgumentException("El plazo para verificar debe ser positivo.");
    }
  }

  /** Los pedidos que ya pasaron el plazo sin verificarse. */
  public List<UUID> vencidos() {
    return repositorioPedidos.buscarIdsEnEstadoCreadosAntesDe(
        EstadoPedido.CONFIRMADO_CONTRAENTREGA,
        reloj.ahora().minus(plazoParaVerificar),
        MAXIMO_POR_CORRIDA);
  }

  /**
   * Cancela uno. Devuelve si lo canceló: entre la lista y esto alguien pudo verificarlo, y entonces
   * no se toca.
   */
  public boolean ejecutar(UUID pedidoId) {
    Pedido pedido = repositorioPedidos.buscarPorIdParaModificar(pedidoId).orElse(null);
    Instant ahora = reloj.ahora();
    if (pedido == null
        || pedido.estado() != EstadoPedido.CONFIRMADO_CONTRAENTREGA
        || !pedido.creadoEn().isBefore(ahora.minus(plazoParaVerificar))) {
      return false;
    }
    pedido.transicionar(
        EstadoPedido.CANCELADO,
        "sistema",
        "contraentrega sin verificar dentro de " + plazoParaVerificar.toHours() + " horas",
        ahora);
    for (LineaPedido linea : pedido.lineas()) {
      liberar(linea, ahora);
    }
    repositorioPedidos.guardar(pedido);
    avisar(pedido);
    return true;
  }

  private void liberar(LineaPedido linea, Instant ahora) {
    repositorioInventario
        .buscarPorVarianteId(linea.varianteId())
        .ifPresent(
            inventario -> {
              try {
                inventario.liberar(linea.idReserva(), "contraentrega sin verificar", ahora);
                repositorioInventario.guardar(inventario);
              } catch (ReservaYaProcesadaException | ReservaNoEncontradaException yaResuelta) {
                // Nada que liberar: la reserva ya no estaba abierta.
              }
            });
  }

  private void avisar(Pedido pedido) {
    try {
      enviadorDeCorreo.enviar(
          pedido.correo(),
          textos.texto(
              TextoDeCorreo.PEDIDO_CONTRAENTREGA_VENCIDA_ASUNTO, pedido.numeroPedido().valor()),
          textos.texto(
                  TextoDeCorreo.PEDIDO_CONTRAENTREGA_VENCIDA_CUERPO, pedido.numeroPedido().valor())
              + textos.texto(TextoDeCorreo.PEDIDO_CANCELACION_SIN_COBRO)
              + textos.texto(TextoDeCorreo.PEDIDO_CANCELACION_CIERRE));
    } catch (CorreoNoEnviadoException registradoPorElAdaptador) {
      // Mismo criterio que CancelarPedido (adr/0045): encolar se une a esta transacción.
    }
  }
}
