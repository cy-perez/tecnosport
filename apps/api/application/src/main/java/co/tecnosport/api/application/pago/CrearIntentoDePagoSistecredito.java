package co.tecnosport.api.application.pago;

import co.tecnosport.api.application.compartido.EnTransaccionPropia;
import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.pedido.PedidoNoEncontradoException;
import co.tecnosport.api.application.pedido.RepositorioPedidos;
import co.tecnosport.api.domain.pago.Pago;
import co.tecnosport.api.domain.pago.ReferenciaPago;
import co.tecnosport.api.domain.pedido.EstadoPedido;
import co.tecnosport.api.domain.pedido.Pedido;
import co.tecnosport.api.domain.pedido.ProveedorDePago;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Crea el intento de pago de un pedido que se paga con Sistecrédito ({@code adr/0048}).
 *
 * <p>Hermano de {@link CrearIntentoDePago}, no una rama suya: lo que comparten —buscar el pedido,
 * exigir {@code PAGO_PENDIENTE}, numerar el intento, guardar un {@link Pago}— son cuatro líneas, y
 * lo que los separa es todo lo demás. Aquí el servidor llama a la pasarela y espera una URL; allá
 * firma unos datos para que el navegador arme la suya.
 *
 * <p>El monto nunca lo trae el cliente: sale de {@link Pedido#total()}, que ya congeló precios
 * reales (regla dura #7). Lo único que el cliente aporta es el documento de quien pide el crédito,
 * que es suyo y que no se guarda en ninguna parte.
 *
 * <h2>Por qué esto son tres transacciones y no una</h2>
 *
 * En la mitad hay un tercero que <b>abre una solicitud de crédito a nombre de una persona</b>, y
 * eso ninguna transacción de base de datos lo revierte. Es exactamente el caso para el que existe
 * {@link EnTransaccionPropia} (adr/0033, {@code apps/api/CLAUDE.md}).
 *
 * <p>La primera versión guardaba el {@link Pago} y después lanzaba la excepción del rechazo, con un
 * comentario que decía "el id se guarda SIEMPRE". Era falso: el controlador envolvía todo en un
 * {@code TransactionTemplate}, que revierte ante cualquier {@code RuntimeException}, así que el
 * rechazo se llevaba por delante la fila. Lo que eso costaba, en cadena:
 *
 * <ul>
 *   <li>Una transacción viva en Sistecrédito con nuestra factura y sin {@code Pago} local: la
 *       conciliación no la ve nunca, y si después se aprueba, la notificación cae en {@code
 *       PAGO_NO_ENCONTRADO} y sale como una línea de registro.
 *   <li>Y lo determinista: el número de intento sale de contar los pagos del pedido. Revertido el
 *       pago, el contador se queda en cero y <b>cada reintento vuelve a generar la misma
 *       factura</b>, que Sistecrédito ya tiene activa y rechaza con su error {@code 738}. El pedido
 *       quedaba imposible de pagar para siempre.
 * </ul>
 *
 * <p>Los dos rechazos más probables —{@code 801} y {@code 802}— entran justo por esa rama.
 *
 * <p>El segundo motivo para partirlo es el sondeo: la llamada a la pasarela puede tardar más de un
 * minuto entre reintentos, y hacerla con una transacción abierta retiene una conexión del pool todo
 * ese tiempo. Diez compradores simultáneos dejaban a la API entera sin conexiones.
 */
public final class CrearIntentoDePagoSistecredito {

  /**
   * Los idiomas que el sitio publica. Lista cerrada y no "lo que venga": el idioma se incrusta en
   * una URL que le entregamos a un tercero para que redirija al comprador, así que aceptar
   * cualquier cadena convertiría este campo en una redirección abierta con nuestro dominio.
   */
  private static final Set<String> IDIOMAS = Set.of("es", "en");

  private static final String IDIOMA_POR_OMISION = "es";
  private static final String MARCADOR_IDIOMA = "{idioma}";

  private final RepositorioPedidos repositorioPedidos;
  private final RepositorioPagos repositorioPagos;
  private final PasarelaSistecredito pasarela;
  private final EnTransaccionPropia enTransaccionPropia;
  private final Reloj reloj;
  private final String urlRespuesta;
  private final String urlConfirmacion;
  private final boolean sandbox;
  private final String estadoSimulado;

  public CrearIntentoDePagoSistecredito(
      RepositorioPedidos repositorioPedidos,
      RepositorioPagos repositorioPagos,
      PasarelaSistecredito pasarela,
      EnTransaccionPropia enTransaccionPropia,
      Reloj reloj,
      String urlRespuesta,
      String urlConfirmacion,
      boolean sandbox,
      String estadoSimulado) {
    this.repositorioPedidos =
        Objects.requireNonNull(repositorioPedidos, "El repositorio de pedidos no puede ser nulo.");
    this.repositorioPagos =
        Objects.requireNonNull(repositorioPagos, "El repositorio de pagos no puede ser nulo.");
    this.pasarela = Objects.requireNonNull(pasarela, "La pasarela no puede ser nula.");
    this.enTransaccionPropia =
        Objects.requireNonNull(enTransaccionPropia, "El ejecutor transaccional no puede ser nulo.");
    this.reloj = Objects.requireNonNull(reloj, "El reloj no puede ser nulo.");
    this.urlRespuesta =
        Objects.requireNonNull(urlRespuesta, "La URL de respuesta no puede ser nula.");
    this.urlConfirmacion =
        Objects.requireNonNull(urlConfirmacion, "La URL de confirmación no puede ser nula.");
    this.sandbox = sandbox;
    this.estadoSimulado = estadoSimulado;
  }

  /**
   * A dónde vuelve el comprador. Dos cosas viajan aquí y las dos por un motivo concreto.
   *
   * <p><b>El idioma</b>, porque las rutas del sitio llevan prefijo y la ruta comodín redirige a
   * {@code /es} <b>perdiendo los parámetros</b>: volver a una URL sin prefijo se traga el retorno
   * entero. Sale de una lista cerrada, no de lo que mande el navegador, o el campo sería una
   * redirección abierta con nuestro propio dominio.
   *
   * <p><b>El pedido y el correo, como segmentos de ruta y no como parámetros de consulta.</b> La
   * pantalla de estado los necesita para consultar el seguimiento, y Sistecrédito solo devuelve lo
   * suyo ({@code paymentRef}, {@code transactionId}, {@code orderId}) concatenado a esta URL — sin
   * que las guías digan si concatena con {@code ?} o con {@code &}. Si fueran parámetros y la
   * pasarela concatenara con {@code ?}, la cadena quedaría con dos signos de interrogación y el
   * navegador no leería ninguno de los dos lados. En la ruta sobreviven pase lo que pase.
   *
   * <p>Sin esto, <b>todo</b> comprador que pagara con Sistecrédito aterrizaba en "no encontramos
   * este pedido" después de haber pagado: la pantalla de estado devuelve vacío sin esos dos datos y
   * no tiene forma de pedirlos.
   */
  private String urlRespuestaPara(String idioma, java.util.UUID pedidoId, String correo) {
    String elegido = IDIOMAS.contains(idioma) ? idioma : IDIOMA_POR_OMISION;
    return urlRespuesta.replace(MARCADOR_IDIOMA, elegido)
        + "/"
        + pedidoId
        + "/"
        + URLEncoder.encode(correo, StandardCharsets.UTF_8);
  }

  public IntentoDePagoSistecredito ejecutar(CrearIntentoDePagoSistecreditoComando comando) {
    Objects.requireNonNull(comando, "El comando no puede ser nulo.");
    Objects.requireNonNull(comando.documento(), "El documento del comprador no puede ser nulo.");

    // (1) Confirmado ANTES de hablar con nadie. A partir de aquí el intento existe pase lo que
    // pase, y el número de intento del siguiente ya no puede repetir esta factura.
    IntentoAbierto abierto = enTransaccionPropia.ejecutar(() -> abrirIntento(comando.pedidoId()));
    Pago pago = abierto.pago();

    // (2) Sin ninguna transacción abierta: aquí dentro hay un sondeo que puede durar más de un
    // minuto, y una conexión del pool retenida todo ese rato es una conexión que le falta al
    // catálogo, al carrito y a los otros medios de pago.
    TransaccionSistecredito transaccion;
    try {
      transaccion =
          pasarela.crear(
              new SolicitudTransaccionSistecredito(
                  pago.referencia(),
                  "Pedido " + pago.referencia().valor(),
                  pago.monto(),
                  comando.documento(),
                  urlRespuestaPara(comando.idioma(), abierto.pedidoId(), abierto.correo()),
                  urlConfirmacion,
                  sandbox,
                  estadoSimulado));
    } catch (RuntimeException e) {
      // El pago queda guardado y sin id de transacción: el mismo caso que el comprador de Wompi
      // que cierra la pestaña antes de volver. No lo recoge la conciliación —no hay id que
      // consultar— y el reintento abre un intento nuevo con otra factura, que es lo correcto:
      // no sabemos si la pasarela llegó a crear algo.
      throw e;
    }

    // (3) El id, en su propia transacción y siempre, incluso cuando la transacción nació
    // rechazada: es lo único que permite consultarla después.
    enTransaccionPropia.ejecutar(() -> registrarId(pago.referencia(), transaccion.id()));

    return transaccion
        .urlDeRedireccion()
        .map(url -> new IntentoDePagoSistecredito(pago.referencia(), pago.monto(), url))
        .orElseThrow(
            () ->
                new SistecreditoNoEntregoLaUrlDePagoException(
                    transaccion.estado(),
                    transaccion.codigoMedioDePago(),
                    transaccion.descripcion()));
  }

  /** Lo que (1) tiene que dejarle a (2): el intento, y los dos datos de la URL de retorno. */
  private record IntentoAbierto(Pago pago, UUID pedidoId, String correo) {}

  private IntentoAbierto abrirIntento(UUID pedidoId) {
    Pedido pedido =
        repositorioPedidos
            .buscarPorId(pedidoId)
            .orElseThrow(() -> new PedidoNoEncontradoException(pedidoId));
    if (pedido.estado() != EstadoPedido.PAGO_PENDIENTE) {
      throw new PedidoNoEstaEnPagoPendienteException(pedido.estado());
    }
    if (pedido.metodoPago().pasarela() != ProveedorDePago.SISTECREDITO) {
      throw new MetodoDePagoNoEsDeSistecreditoException(pedido.metodoPago());
    }
    int numeroDeIntento = repositorioPagos.buscarPorPedidoId(pedido.id()).size() + 1;
    ReferenciaPago referencia =
        new ReferenciaPago(pedido.numeroPedido().valor() + "-" + numeroDeIntento);
    Pago pago =
        Pago.crear(pedido.id(), referencia, pedido.metodoPago(), pedido.total(), reloj.ahora());
    repositorioPagos.guardar(pago);
    return new IntentoAbierto(pago, pedido.id(), pedido.correo().valor());
  }

  /**
   * Se recarga el pago en vez de reutilizar la instancia de (1): aquella pertenece a una
   * transacción que ya se cerró, y guardarla desde otra es pedirle a JPA que adivine.
   */
  private Void registrarId(ReferenciaPago referencia, String idTransaccion) {
    repositorioPagos
        .buscarPorReferencia(referencia)
        .ifPresent(
            pagoVivo -> {
              pagoVivo.registrarIdTransaccionPasarela(idTransaccion);
              repositorioPagos.guardar(pagoVivo);
            });
    return null;
  }
}
