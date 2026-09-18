package co.tecnosport.api.application.pedido;

import co.tecnosport.api.application.compartido.EnviadorDeCorreo;
import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.compartido.TextoDeCorreo;
import co.tecnosport.api.application.compartido.TextosDeCorreo;
import co.tecnosport.api.application.envio.EmisorDeGuias;
import co.tecnosport.api.application.envio.RepositorioEmisiones;
import co.tecnosport.api.application.envio.ResultadoCancelacion;
import co.tecnosport.api.application.inventario.RepositorioInventario;
import co.tecnosport.api.application.reintegro.ReintegroRequeridoException;
import co.tecnosport.api.application.reintegro.RepositorioReintegros;
import co.tecnosport.api.application.reintegro.TopeDeReintegro;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.envio.EmisionDeGuia;
import co.tecnosport.api.domain.envio.EstadoEmision;
import co.tecnosport.api.domain.inventario.Inventario;
import co.tecnosport.api.domain.pedido.EstadoPedido;
import co.tecnosport.api.domain.pedido.LineaPedido;
import co.tecnosport.api.domain.pedido.MetodoPago;
import co.tecnosport.api.domain.pedido.MotivoCancelacion;
import co.tecnosport.api.domain.pedido.Pedido;
import co.tecnosport.api.domain.reintegro.MotivoReintegro;
import co.tecnosport.api.domain.reintegro.Reintegro;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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
 * <p>El monto lo acota {@link TopeDeReintegro}, que cuenta lo ya devuelto por este pedido y no solo
 * esta cancelación.
 *
 * <p>El inventario vuelve por {@code Inventario.devolver}, que decide entre entrada y liberación
 * según cómo quedó la reserva: un pago aprobado ya la confirmó, un contraentrega la tiene abierta.
 *
 * <p><b>Y las guías se anulan, que es lo que faltaba.</b> La guía se emite estando {@code
 * EN_PREPARACION} y {@code EN_PREPARACION -> CANCELADO} es una transición válida, así que este caso
 * de uso podía dejar —y dejaba— una guía viva y cobrable de un pedido que ya no existe: un paquete
 * que la transportadora recoge, entrega y factura sin que nadie lo note hasta el extracto. No hacía
 * falta ningún camino raro para llegar ahí; bastaba con cancelar desde el panel un pedido cuya guía
 * ya se había pedido.
 *
 * <p><b>Lo que no se hace, y es la regla que ordena todo lo demás: la anulación no puede tumbar la
 * cancelación.</b> Si la plataforma se niega o no contesta, el pedido queda cancelado igual, el
 * inventario vuelve igual y el reintegro se registra igual; la emisión queda {@link
 * EstadoEmision#SIN_ANULAR} y aparece en la bandeja de revisión. Un comprador sin su plata porque
 * un proveedor no contestó es peor que una guía huérfana que alguien anula a mano desde el panel.
 */
public final class CancelarPedido {

  private final RepositorioPedidos repositorioPedidos;
  private final RepositorioInventario repositorioInventario;
  private final RepositorioReintegros repositorioReintegros;
  private final RepositorioEmisiones repositorioEmisiones;
  private final EmisorDeGuias emisorDeGuias;
  private final TopeDeReintegro tope;
  private final EnviadorDeCorreo enviadorDeCorreo;
  private final TextosDeCorreo textos;
  private final Reloj reloj;

  public CancelarPedido(
      RepositorioPedidos repositorioPedidos,
      RepositorioInventario repositorioInventario,
      RepositorioReintegros repositorioReintegros,
      RepositorioEmisiones repositorioEmisiones,
      EmisorDeGuias emisorDeGuias,
      TopeDeReintegro tope,
      EnviadorDeCorreo enviadorDeCorreo,
      TextosDeCorreo textos,
      Reloj reloj) {
    this.repositorioPedidos = Objects.requireNonNull(repositorioPedidos);
    this.repositorioInventario = Objects.requireNonNull(repositorioInventario);
    this.repositorioReintegros = Objects.requireNonNull(repositorioReintegros);
    this.repositorioEmisiones = Objects.requireNonNull(repositorioEmisiones);
    this.emisorDeGuias = Objects.requireNonNull(emisorDeGuias);
    this.tope = Objects.requireNonNull(tope);
    this.enviadorDeCorreo = Objects.requireNonNull(enviadorDeCorreo);
    this.textos = Objects.requireNonNull(textos);
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
      throw ReintegroRequeridoException.porqueElDineroYaEntro(pedido.id());
    }

    Instant ahora = reloj.ahora();
    pedido.transicionar(EstadoPedido.CANCELADO, comando.actor(), motivo(comando), ahora);

    // Las guías se anulan ANTES de tocar el inventario, y el orden no es estético: devolver al
    // inventario toma un bloqueo pesimista por variante, y anular una guía son N llamadas HTTP a
    // Skydropx. Con el orden anterior, cancelar desde el panel un pedido de tres bultos de la
    // variante más vendida sostenía sus filas bloqueadas durante toda la conversación con el
    // proveedor: cualquier comprador que intentara confirmar un pedido con esa variante se quedaba
    // esperando en el checkout. `CrearPedido` ya se cuidaba de esto —cotiza antes de reservar, y lo
    // deja escrito— y aquí se hacía justo lo contrario. Lo levantó una revisión adversarial.
    //
    // La transición va primero igual, porque es la que valida: no tiene sentido anular las guías de
    // un pedido que no se puede cancelar.
    anularLasGuias(pedido, ahora);

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
    tope.exigirQueQuepa(pedido.id(), pedido.total(), monto);
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
   * Pide a la plataforma que anule lo que este pedido tenga vivo.
   *
   * <p>Una llamada <b>por envío</b> y no por emisión: en multienvío cada bulto es un envío
   * independiente que se cancela por separado (docs/13-skydropx-capacidades.md §6.4), y que uno
   * falle no dice nada de los otros. La emisión queda {@link EstadoEmision#ANULADA} solo si
   * <em>todos</em> los suyos se anularon; con uno que quede en duda, la emisión entera pide ojo
   * humano, porque el que quedó vivo es el que cuesta.
   */
  private void anularLasGuias(Pedido pedido, Instant ahora) {
    for (EmisionDeGuia emision : repositorioEmisiones.buscarDePedido(pedido.id())) {
      if (!hayAlgoQueAnular(emision)) {
        continue;
      }
      // Sin identificadores no hay a quién pedírselo, y aun así puede haber algo vivo: una emisión
      // SOLICITADA es una peticion que pudo salir, y una INDETERMINADA es una que pudo cobrar. Las
      // dos van a la bandeja en vez de darse por limpias.
      if (emision.enviosEnPlataforma().isEmpty()) {
        emision.sinAnular(
            "La emisión quedó en "
                + emision.estado()
                + " sin identificadores de envío, así que no hay qué pedirle a la plataforma."
                + " Buscar en el panel con la tarifa "
                + emision.idTarifa()
                + " antes de dar el paquete por detenido.",
            ahora);
        repositorioEmisiones.guardar(emision);
        continue;
      }
      List<String> enDuda = new ArrayList<>();
      for (String envio : emision.enviosEnPlataforma()) {
        if (emisorDeGuias.cancelar(envio) instanceof ResultadoCancelacion.NoSePudo fallo) {
          enDuda.add(envio + ": " + fallo.detalle());
        }
      }
      if (enDuda.isEmpty()) {
        emision.anulada(ahora);
      } else {
        emision.sinAnular(
            "Guías que pueden seguir vivas y hay que anular a mano en el panel — "
                + String.join(" | ", enDuda),
            ahora);
      }
      repositorioEmisiones.guardar(emision);
    }
  }

  /**
   * Una emisión fallida no tiene nada vivo —la plataforma ya reembolsó— y una ya anulada no tiene
   * nada que volver a pedir. Todo lo demás sí: incluso una emisión recién solicitada puede tener
   * una petición en vuelo del otro lado.
   */
  private static boolean hayAlgoQueAnular(EmisionDeGuia emision) {
    return emision.estado() != EstadoEmision.FALLIDA && emision.estado() != EstadoEmision.ANULADA;
  }

  /**
   * "Te lo comunicaremos de inmediato", dice el texto, y por eso el aviso va dentro de la misma
   * transacción. Lo que este comentario prometía —que un correo caído tampoco dejara el pedido
   * cancelado— <b>no ocurre</b>: el adaptador se traga el fallo, así que el pedido queda cancelado
   * y el comprador puede no enterarse, que es justo el reclamo que esto venía a evitar. Ver {@link
   * co.tecnosport.api.application.compartido.EnviadorDeCorreo}.
   */
  private void avisar(Pedido pedido, CancelarPedidoComando comando, boolean huboReintegro) {
    TextoDeCorreo explicacion =
        comando.motivo() == MotivoCancelacion.NO_DISPONIBILIDAD
            ? TextoDeCorreo.PEDIDO_CANCELACION_NO_DISPONIBILIDAD
            : TextoDeCorreo.PEDIDO_CANCELACION_PLAZO_INCUMPLIDO;
    TextoDeCorreo dinero =
        huboReintegro
            ? TextoDeCorreo.PEDIDO_CANCELACION_CON_REINTEGRO
            : TextoDeCorreo.PEDIDO_CANCELACION_SIN_COBRO;
    enviadorDeCorreo.enviar(
        pedido.correo(),
        textos.texto(TextoDeCorreo.PEDIDO_CANCELACION_ASUNTO, pedido.numeroPedido().valor()),
        textos.texto(explicacion)
            + textos.texto(dinero)
            + textos.texto(TextoDeCorreo.PEDIDO_CANCELACION_CIERRE));
  }
}
