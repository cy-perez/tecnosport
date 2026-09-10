package co.tecnosport.api.application.pedido;

import co.tecnosport.api.application.compartido.EnviadorDeCorreo;
import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.inventario.RepositorioInventario;
import co.tecnosport.api.application.reintegro.MontoDeReintegroInvalidoException;
import co.tecnosport.api.application.reintegro.RepositorioReintegros;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.inventario.Inventario;
import co.tecnosport.api.domain.pedido.EstadoPedido;
import co.tecnosport.api.domain.pedido.LineaPedido;
import co.tecnosport.api.domain.pedido.MetodoPago;
import co.tecnosport.api.domain.pedido.MotivoCancelacion;
import co.tecnosport.api.domain.pedido.Pedido;
import co.tecnosport.api.domain.reintegro.MotivoReintegro;
import co.tecnosport.api.domain.reintegro.Reintegro;
import java.time.Instant;
import java.util.Objects;

/**
 * Cancela un pedido antes de despacharlo, por causa del negocio: la existencia desapareció después
 * de la compra, o no se entregó dentro del plazo pactado.
 *
 * <p>Los dos caminos estaban prometidos en los términos publicados y ninguno tenía código detrás.
 * No aparecieron buscando figuras legales sino leyendo el documento entero en busca de toda frase
 * que prometiera devolver dinero: viven en "Disponibilidad" y en "Envío y entrega", que nadie lee
 * como secciones de dinero.
 *
 * <p><b>No radica una solicitud de atención</b>, a diferencia de la garantía y la reversión.
 * Aquélla es una petición del comprador con su plazo de respuesta corriendo; esto es el negocio
 * avisando de algo suyo. Meterlo en la bandeja de PQR llenaría de ruido la lista de lo que hay que
 * responder.
 *
 * <p>El dinero se devuelve solo si había entrado. Un contraentrega sin despachar no cobró nada y un
 * pago pendiente tampoco: exigir ahí una constancia obligaría a inventar un reintegro que nunca
 * ocurrió. Cuando sí entró, la constancia es obligatoria — un pedido cancelado sin reintegro
 * después de haber cobrado es plata retenida sin explicación.
 *
 * <p>El inventario vuelve por {@code Inventario.devolver}, que decide entre entrada y liberación
 * según cómo quedó la reserva: un pago aprobado ya la confirmó, un contraentrega la tiene abierta.
 */
public final class CancelarPedido {

  private final RepositorioPedidos repositorioPedidos;
  private final RepositorioInventario repositorioInventario;
  private final RepositorioReintegros repositorioReintegros;
  private final EnviadorDeCorreo enviadorDeCorreo;
  private final Reloj reloj;

  public CancelarPedido(
      RepositorioPedidos repositorioPedidos,
      RepositorioInventario repositorioInventario,
      RepositorioReintegros repositorioReintegros,
      EnviadorDeCorreo enviadorDeCorreo,
      Reloj reloj) {
    this.repositorioPedidos = Objects.requireNonNull(repositorioPedidos);
    this.repositorioInventario = Objects.requireNonNull(repositorioInventario);
    this.repositorioReintegros = Objects.requireNonNull(repositorioReintegros);
    this.enviadorDeCorreo = Objects.requireNonNull(enviadorDeCorreo);
    this.reloj = Objects.requireNonNull(reloj);
  }

  public Pedido ejecutar(CancelarPedidoComando comando) {
    Objects.requireNonNull(comando, "El comando no puede ser nulo.");
    Pedido pedido =
        repositorioPedidos
            .buscarPorId(comando.pedidoId())
            .orElseThrow(() -> new PedidoNoEncontradoException(comando.pedidoId()));

    boolean elDineroYaEntro = elDineroYaEntro(pedido);
    if (elDineroYaEntro && (comando.monto() == null || comando.medio() == null)) {
      throw new ReintegroRequeridoException(pedido.id());
    }

    Instant ahora = reloj.ahora();
    pedido.transicionar(EstadoPedido.CANCELADO, comando.actor(), motivo(comando), ahora);
    for (LineaPedido linea : pedido.lineas()) {
      devolverAlInventario(linea, comando.motivo(), ahora);
    }

    if (elDineroYaEntro) {
      registrarReintegro(pedido, comando, ahora);
    }
    repositorioPedidos.guardar(pedido);
    avisar(pedido, comando, elDineroYaEntro);
    return pedido;
  }

  /**
   * Un contraentrega cobra al entregar, así que antes de despachar nunca ha entrado un peso. En los
   * demás métodos, llegar a {@code PAGADO} o a {@code EN_PREPARACION} significa que el pago se
   * aplicó — es la única forma de alcanzar esos estados.
   */
  private static boolean elDineroYaEntro(Pedido pedido) {
    if (pedido.metodoPago() == MetodoPago.CONTRAENTREGA) {
      return false;
    }
    return pedido.estado() == EstadoPedido.PAGADO || pedido.estado() == EstadoPedido.EN_PREPARACION;
  }

  private void devolverAlInventario(LineaPedido linea, MotivoCancelacion motivo, Instant ahora) {
    Inventario inventario =
        repositorioInventario
            .buscarPorVarianteId(linea.varianteId())
            .orElseThrow(() -> new VarianteNoEncontradaException(linea.varianteId()));
    inventario.devolver(linea.idReserva(), "cancelacion: " + motivo, ahora);
    repositorioInventario.guardar(inventario);
  }

  private void registrarReintegro(Pedido pedido, CancelarPedidoComando comando, Instant ahora) {
    Dinero monto = Dinero.deCop(comando.monto());
    if (monto.valor().compareTo(pedido.total().valor()) > 0) {
      throw new MontoDeReintegroInvalidoException(monto, pedido.total());
    }
    repositorioReintegros.guardar(
        Reintegro.registrar(
            pedido.id(),
            motivoDelReintegro(comando.motivo()),
            pedido.id(),
            monto,
            comando.medio(),
            comando.comprobante(),
            ahora,
            comando.actor()));
  }

  /**
   * El origen del reintegro es el propio pedido y no una solicitud, porque aquí no hay solicitud
   * del comprador: la cancelación la decide el negocio.
   */
  private static MotivoReintegro motivoDelReintegro(MotivoCancelacion motivo) {
    return motivo == MotivoCancelacion.NO_DISPONIBILIDAD
        ? MotivoReintegro.NO_DISPONIBILIDAD
        : MotivoReintegro.PLAZO_INCUMPLIDO;
  }

  private static String motivo(CancelarPedidoComando comando) {
    return "cancelado: " + comando.motivo();
  }

  /**
   * "Te lo comunicaremos de inmediato", dice el texto. Dentro de la misma transacción, mismo
   * criterio que el resto: si el correo falla, tampoco queda el pedido cancelado — un comprador que
   * no se entera de que su pedido no va a llegar es justo el reclamo que esto viene a evitar.
   */
  private void avisar(Pedido pedido, CancelarPedidoComando comando, boolean huboReintegro) {
    String explicacion =
        comando.motivo() == MotivoCancelacion.NO_DISPONIBILIDAD
            ? "<p>Un producto de tu pedido dejo de estar disponible despues de tu compra, asi que"
                + " cancelamos el pedido.</p>"
            : "<p>No pudimos entregarte dentro del plazo, asi que cancelamos el pedido.</p>";
    String dinero =
        huboReintegro
            ? "<p>Te reintegramos el dinero por el medio acordado. Segun el medio, puede tardar en"
                + " reflejarse en tu cuenta.</p>"
            : "<p>No se te cobro nada por este pedido.</p>";
    enviadorDeCorreo.enviar(
        pedido.correo(),
        "Cancelamos tu pedido " + pedido.numeroPedido().valor() + " — TecnoSport",
        explicacion + dinero + "<p>Si quieres volver a intentarlo, escribenos y te ayudamos.</p>");
  }
}
