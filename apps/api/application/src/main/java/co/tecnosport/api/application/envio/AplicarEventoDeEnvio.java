package co.tecnosport.api.application.envio;

import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.pedido.MarcarEntregado;
import co.tecnosport.api.application.pedido.MarcarEntregadoComando;
import co.tecnosport.api.application.pedido.RechazarEnEntrega;
import co.tecnosport.api.application.pedido.RechazarEnEntregaComando;
import co.tecnosport.api.application.pedido.RepositorioPedidos;
import co.tecnosport.api.domain.compartido.GeneradorIdentificador;
import co.tecnosport.api.domain.envio.Envio;
import co.tecnosport.api.domain.envio.EstadoEnvio;
import co.tecnosport.api.domain.envio.EventoSeguimiento;
import co.tecnosport.api.domain.pedido.EstadoPedido;
import co.tecnosport.api.domain.pedido.Pedido;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Aplica un movimiento del paquete: lo registra en el rastro y, si es uno de los tres que importan,
 * mueve el pedido (adr/0022).
 *
 * <p><strong>El componente compartido</strong> del que habla ese ADR: por aquí entran tanto el
 * webhook como la conciliación programada, y entran por el mismo sitio a propósito. Dos caminos con
 * la misma responsabilidad y código distinto se separan el día que alguien arregle uno solo — y el
 * segundo camino existe justamente porque el primero se pierde eventos.
 *
 * <p><strong>Nada de esto es un error.</strong> Una guía que no conocemos, un evento repetido, un
 * estado que no mueve nada: los cuatro desenlaces son respuestas legítimas y ninguno lanza. El
 * webhook responde 200 en todos, porque reintentar no arregla ninguno.
 *
 * <p><strong>Los efectos no se reimplementan aquí.</strong> Entregar confirma reservas y encadena
 * el recaudo; rechazar libera inventario. Eso ya lo saben {@link MarcarEntregado} y {@link
 * RechazarEnEntrega}, y duplicarlo sería tener dos verdades sobre qué pasa con el inventario cuando
 * un paquete se entrega.
 */
public final class AplicarEventoDeEnvio {

  private final RepositorioEnvios repositorioEnvios;
  private final RepositorioPedidos repositorioPedidos;
  private final MarcarEntregado marcarEntregado;
  private final RechazarEnEntrega rechazarEnEntrega;
  private final Reloj reloj;

  public AplicarEventoDeEnvio(
      RepositorioEnvios repositorioEnvios,
      RepositorioPedidos repositorioPedidos,
      MarcarEntregado marcarEntregado,
      RechazarEnEntrega rechazarEnEntrega,
      Reloj reloj) {
    this.repositorioEnvios = Objects.requireNonNull(repositorioEnvios);
    this.repositorioPedidos = Objects.requireNonNull(repositorioPedidos);
    this.marcarEntregado = Objects.requireNonNull(marcarEntregado);
    this.rechazarEnEntrega = Objects.requireNonNull(rechazarEnEntrega);
    this.reloj = Objects.requireNonNull(reloj);
  }

  public ResultadoEventoDeEnvio ejecutar(AplicarEventoDeEnvioComando comando) {
    Objects.requireNonNull(comando, "El comando no puede ser nulo.");
    Optional<Envio> encontrado = repositorioEnvios.buscarPorGuia(comando.guia());
    if (encontrado.isEmpty()) {
      return ResultadoEventoDeEnvio.GUIA_DESCONOCIDA;
    }

    Envio envio = encontrado.get();
    Instant ahora = reloj.ahora();
    // En la guía de la que habla el evento, no en el envío entero: un envío puede llevar varias y
    // cada paquete se mueve solo (adr/0031). Es la misma con la que se encontró el envío, así que
    // un `false` aquí solo puede ser un evento repetido.
    boolean esNuevo =
        envio.registrarEvento(
            comando.guia(),
            new EventoSeguimiento(
                GeneradorIdentificador.nuevo(),
                comando.estado(),
                comando.descripcion(),
                comando.ocurrioEn(),
                ahora,
                comando.idExterno()));
    if (!esNuevo) {
      return ResultadoEventoDeEnvio.REPETIDO;
    }
    repositorioEnvios.guardar(envio);

    boolean movioElPedido = aplicarAlPedido(envio, comando, ahora);
    return movioElPedido
        ? ResultadoEventoDeEnvio.REGISTRADO_Y_APLICADO
        : ResultadoEventoDeEnvio.REGISTRADO;
  }

  /**
   * Solo tres estados mueven el pedido, y cada uno solo desde donde tiene sentido. La guarda por
   * estado actual no es decoración: un pedido que un administrador ya marcó entregado a mano
   * recibiría después el {@code delivered} de la transportadora, y aplicarlo reventaría contra la
   * máquina de estados — o peor, reabriría plazos legales que ya estaban corriendo.
   *
   * <p><b>Y hace falta que <i>todas</i> las guías hayan llegado al mismo sitio</b>, no solo la que
   * trae este evento. Ver {@code Envio.todasLasGuiasEn}.
   *
   * <p><b>Lo que queda sin resolver, y es un hueco del modelo, no un olvido:</b> un pedido de dos
   * bultos donde uno se entrega y el otro se devuelve no es ni entregado ni rechazado, y el pedido
   * tiene un solo estado para decirlo. Hoy ese pedido <b>no se mueve</b> —se queda en {@code
   * DESPACHADO}— y la guía devuelta queda con su evento registrado para que alguien lo vea. Es el
   * lado por el que se prefiere fallar: no arrancar plazos legales sobre mercancía que no llegó, y
   * no liberar la reserva de un artículo que el comprador ya tiene en la mano. Resolverlo de verdad
   * pide cumplimiento por línea, que es un cambio de modelo con su propia decisión.
   */
  private boolean aplicarAlPedido(Envio envio, AplicarEventoDeEnvioComando comando, Instant ahora) {
    Optional<Pedido> pedido = repositorioPedidos.buscarPorId(envio.pedidoId());
    if (pedido.isEmpty()) {
      return false;
    }
    EstadoPedido estado = pedido.get().estado();

    if (comando.estado() == EstadoEnvio.RECOGIDO) {
      return confirmarDespacho(pedido.get(), comando, ahora);
    }
    // El pedido tiene un estado y el envío puede tener varias guías: hasta que no llegan todas, la
    // compra no está entregada. Con el criterio anterior —el primer evento movía el pedido— un
    // pedido de dos bultos arrancaba el retracto y la garantía con el primero, sobre mercancía que
    // el comprador no tenía; y en contraentrega lo daba por cobrado. Lo levantó una revisión
    // adversarial. El caso mixto —una entregada y otra devuelta— no se puede expresar en un solo
    // estado de pedido y queda a propósito sin mover nada: ver el javadoc de abajo.
    if (comando.estado() == EstadoEnvio.ENTREGADO
        && estado == EstadoPedido.DESPACHADO
        && envio.todasLasGuiasEn(EstadoEnvio.ENTREGADO)) {
      marcarEntregado.ejecutar(new MarcarEntregadoComando(envio.pedidoId(), comando.actor()));
      return true;
    }
    if (comando.estado() == EstadoEnvio.EN_DEVOLUCION
        && estado == EstadoPedido.DESPACHADO
        && envio.todasLasGuiasEn(EstadoEnvio.EN_DEVOLUCION)) {
      rechazarEnEntrega.ejecutar(
          new RechazarEnEntregaComando(
              envio.pedidoId(), motivoDeDevolucion(comando), comando.actor()));
      return true;
    }
    return false;
  }

  /**
   * Hoy el pedido ya está {@code DESPACHADO} cuando existe el envío —lo despacha una persona desde
   * el panel, y ahí nace la guía—, así que un {@code picked_up} normalmente no mueve nada. Se deja
   * escrito igual porque el día que la guía se emita automáticamente el despacho dejará de ser el
   * momento en que alguien pulsa un botón, y este será el evento que lo confirme.
   */
  private boolean confirmarDespacho(
      Pedido pedido, AplicarEventoDeEnvioComando comando, Instant ahora) {
    if (!pedido.estado().puedeTransicionarA(EstadoPedido.DESPACHADO)) {
      return false;
    }
    pedido.transicionar(
        EstadoPedido.DESPACHADO, comando.actor(), "recogido por la transportadora", ahora);
    repositorioPedidos.guardar(pedido);
    return true;
  }

  /**
   * El motivo viaja al historial del pedido y de ahí a la vista del panel, así que no puede ir
   * vacío: {@code RechazarEnEntregaComando} lo exige por la misma razón.
   */
  private static String motivoDeDevolucion(AplicarEventoDeEnvioComando comando) {
    return comando.descripcion() == null || comando.descripcion().isBlank()
        ? "devolución reportada por la transportadora"
        : comando.descripcion();
  }
}
