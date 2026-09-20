package co.tecnosport.api.application.pago;

import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.pedido.PedidoNoEncontradoException;
import co.tecnosport.api.application.pedido.RepositorioPedidos;
import co.tecnosport.api.domain.pago.Pago;
import co.tecnosport.api.domain.pago.ReferenciaPago;
import co.tecnosport.api.domain.pedido.EstadoPedido;
import co.tecnosport.api.domain.pedido.Pedido;
import co.tecnosport.api.domain.pedido.ProveedorDePago;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;

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
  private final Reloj reloj;
  private final String urlRespuesta;
  private final String urlConfirmacion;
  private final boolean sandbox;
  private final String estadoSimulado;

  public CrearIntentoDePagoSistecredito(
      RepositorioPedidos repositorioPedidos,
      RepositorioPagos repositorioPagos,
      PasarelaSistecredito pasarela,
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
    this.reloj = Objects.requireNonNull(reloj, "El reloj no puede ser nulo.");
    this.urlRespuesta =
        Objects.requireNonNull(urlRespuesta, "La URL de respuesta no puede ser nula.");
    this.urlConfirmacion =
        Objects.requireNonNull(urlConfirmacion, "La URL de confirmación no puede ser nula.");
    this.sandbox = sandbox;
    this.estadoSimulado = estadoSimulado;
  }

  /**
   * Las rutas del sitio llevan prefijo de idioma y la ruta comodín redirige a {@code /es}
   * <b>perdiendo los parámetros</b>, así que volver a una URL sin prefijo se traga el retorno
   * entero: el comprador aterriza en la portada y el pedido parece no existir.
   */
  private String urlRespuestaPara(String idioma) {
    String elegido = IDIOMAS.contains(idioma) ? idioma : IDIOMA_POR_OMISION;
    return urlRespuesta.replace(MARCADOR_IDIOMA, elegido);
  }

  public IntentoDePagoSistecredito ejecutar(CrearIntentoDePagoSistecreditoComando comando) {
    Objects.requireNonNull(comando, "El comando no puede ser nulo.");
    Objects.requireNonNull(comando.documento(), "El documento del comprador no puede ser nulo.");
    Pedido pedido =
        repositorioPedidos
            .buscarPorId(comando.pedidoId())
            .orElseThrow(() -> new PedidoNoEncontradoException(comando.pedidoId()));
    if (pedido.estado() != EstadoPedido.PAGO_PENDIENTE) {
      throw new PedidoNoEstaEnPagoPendienteException(pedido.estado());
    }
    if (pedido.metodoPago().pasarela() != ProveedorDePago.SISTECREDITO) {
      throw new MetodoDePagoNoEsDeSistecreditoException(pedido.metodoPago());
    }

    int numeroDeIntento = repositorioPagos.buscarPorPedidoId(pedido.id()).size() + 1;
    ReferenciaPago referencia =
        new ReferenciaPago(pedido.numeroPedido().valor() + "-" + numeroDeIntento);

    Instant ahora = reloj.ahora();
    Pago pago = Pago.crear(pedido.id(), referencia, pedido.metodoPago(), pedido.total(), ahora);

    TransaccionSistecredito transaccion =
        pasarela.crear(
            new SolicitudTransaccionSistecredito(
                referencia,
                "Pedido " + pedido.numeroPedido().valor(),
                pedido.total(),
                comando.documento(),
                urlRespuestaPara(comando.idioma()),
                urlConfirmacion,
                sandbox,
                estadoSimulado));

    // El id se guarda SIEMPRE, incluso cuando la transacción nació rechazada: es lo que permite
    // consultarla después, y un intento cuyo id se perdió es un intento que solo existe del lado
    // de Sistecrédito. Va antes de decidir si hay URL, a propósito.
    pago.registrarIdTransaccionPasarela(transaccion.id());
    repositorioPagos.guardar(pago);

    return transaccion
        .urlDeRedireccion()
        .map(url -> new IntentoDePagoSistecredito(referencia, pedido.total(), url))
        .orElseThrow(
            () ->
                new SistecreditoNoEntregoLaUrlDePagoException(
                    transaccion.estado(),
                    transaccion.codigoMedioDePago(),
                    transaccion.descripcion()));
  }
}
