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
import co.tecnosport.api.domain.pedido.MetodoPago;
import co.tecnosport.api.domain.pedido.Pedido;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Cancela una recogida con pago en el punto que nadie vino a recoger en las 48 horas siguientes al
 * aviso de que estaba lista ({@link Pedido#PLAZO_PARA_RECOGER}), y libera su mercancía (ADR-0094).
 *
 * <p><b>Solo las que se pagan en el punto.</b> No se cobró nada, así que cancelar no deja a nadie
 * sin su dinero. Una recogida pagada en línea no se cancela sola: {@code CancelarPedido} exige la
 * constancia del reintegro, y el reintegro lo hace una persona. Esa la ve vencida el panel, que
 * pinta {@code recogidaVenceEn}, y se cancela desde allí con la devolución.
 */
public final class VencerRecogidasSinRetirar {

  private static final int MAXIMO_POR_CORRIDA = 50;

  private final RepositorioPedidos repositorioPedidos;
  private final RepositorioInventario repositorioInventario;
  private final EnviadorDeCorreo enviadorDeCorreo;
  private final TextosDeCorreo textos;
  private final Reloj reloj;

  public VencerRecogidasSinRetirar(
      RepositorioPedidos repositorioPedidos,
      RepositorioInventario repositorioInventario,
      EnviadorDeCorreo enviadorDeCorreo,
      TextosDeCorreo textos,
      Reloj reloj) {
    this.repositorioPedidos = Objects.requireNonNull(repositorioPedidos);
    this.repositorioInventario = Objects.requireNonNull(repositorioInventario);
    this.enviadorDeCorreo = Objects.requireNonNull(enviadorDeCorreo);
    this.textos = Objects.requireNonNull(textos);
    this.reloj = Objects.requireNonNull(reloj);
  }

  /** Las recogidas con pago en el punto que llevan más del plazo listas y sin recoger. */
  public List<UUID> vencidas() {
    return repositorioPedidos.buscarIdsEnEstadoDesdeAntesDe(
        EstadoPedido.LISTO_PARA_RECOGER,
        MetodoPago.PAGO_EN_PUNTO,
        reloj.ahora().minus(Pedido.PLAZO_PARA_RECOGER),
        MAXIMO_POR_CORRIDA);
  }

  /**
   * Cancela una. Devuelve si la canceló: entre la lista y esto pudieron entregarla, y entonces no
   * se toca.
   */
  public boolean ejecutar(UUID pedidoId) {
    Pedido pedido = repositorioPedidos.buscarPorIdParaModificar(pedidoId).orElse(null);
    Instant ahora = reloj.ahora();
    if (pedido == null
        || pedido.metodoPago() != MetodoPago.PAGO_EN_PUNTO
        || !pedido.recogidaVencida(ahora)) {
      return false;
    }
    pedido.transicionar(
        EstadoPedido.CANCELADO,
        "sistema",
        "no se recogió dentro de " + Pedido.PLAZO_PARA_RECOGER.toHours() + " horas",
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
                inventario.liberar(linea.idReserva(), "recogida sin retirar", ahora);
                repositorioInventario.guardar(inventario);
              } catch (ReservaYaProcesadaException | ReservaNoEncontradaException yaResuelta) {
                // Nada que liberar: la reserva ya no estaba abierta.
              }
            });
  }

  private void avisar(Pedido pedido) {
    String numero = pedido.numeroPedido().valor();
    try {
      enviadorDeCorreo.enviar(
          pedido.correo(),
          pedido.nombreParaSaludar().orElse(null),
          textos.texto(TextoDeCorreo.PEDIDO_CANCELACION_ASUNTO, numero),
          textos.texto(TextoDeCorreo.PEDIDO_RECOGIDA_VENCIDA_CUERPO, numero)
              + textos.texto(TextoDeCorreo.PEDIDO_CANCELACION_SIN_COBRO)
              + textos.texto(TextoDeCorreo.PEDIDO_CANCELACION_CIERRE));
    } catch (CorreoNoEnviadoException registradoPorElAdaptador) {
      // Mismo criterio que CancelarPedido (adr/0045): encolar se une a esta transacción.
    }
  }
}
