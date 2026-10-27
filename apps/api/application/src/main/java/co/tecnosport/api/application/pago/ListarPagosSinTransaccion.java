package co.tecnosport.api.application.pago;

import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.pedido.RepositorioPedidos;
import co.tecnosport.api.domain.pedido.ProveedorDePago;
import java.time.Duration;
import java.util.Objects;

/**
 * Los pagos de Wompi que nadie concilia: {@code PENDIENTE}, sin id de transacción y más viejos que
 * el umbral (12 horas por omisión, {@code tecnosport.alertas.pago-sin-transaccion-horas}).
 *
 * <p>El id llega por dos caminos —el webhook y el retorno del navegador—, y si los dos fallan el
 * pago queda fuera de {@code ConciliarPagosPendientes}: la API de Wompi busca por su id, no por la
 * referencia propia, y su documentación pública no ofrece una búsqueda por referencia que se pueda
 * usar con la llave de este comercio. Lo único que queda es que una persona lo busque en el portal
 * de Wompi. Este caso de uso le dice cuáles.
 *
 * <p>Casi todos van a ser checkouts abandonados —el comprador abrió el Web Checkout y no pagó—, y
 * por eso cada uno se puede marcar como revisado ({@link MarcarPagoSinTransaccionRevisado}). El que
 * importa es el otro: el que sí cobró y cuyo aviso se perdió.
 *
 * <p>Solo Wompi: a Sistecrédito el id se lo da la propia creación del intento, y un pago suyo sin
 * id es uno cuya solicitud no llegó a crearse.
 */
public final class ListarPagosSinTransaccion {

  private final RepositorioPagos repositorioPagos;
  private final RepositorioPedidos repositorioPedidos;
  private final Reloj reloj;
  private final Duration umbral;

  public ListarPagosSinTransaccion(
      RepositorioPagos repositorioPagos,
      RepositorioPedidos repositorioPedidos,
      Reloj reloj,
      Duration umbral) {
    this.repositorioPagos = Objects.requireNonNull(repositorioPagos);
    this.repositorioPedidos = Objects.requireNonNull(repositorioPedidos);
    this.reloj = Objects.requireNonNull(reloj);
    this.umbral = Objects.requireNonNull(umbral, "El umbral no puede ser nulo.");
    if (umbral.isNegative() || umbral.isZero()) {
      throw new IllegalArgumentException("El umbral de la alerta debe ser positivo.");
    }
  }

  public PagosSinTransaccion ejecutar() {
    return new PagosSinTransaccion(
        umbral,
        repositorioPagos
            .buscarPendientesSinTransaccionSinRevisar(reloj.ahora().minus(umbral))
            .stream()
            .filter(pago -> pago.metodoPago().pasarela() == ProveedorDePago.WOMPI)
            .flatMap(
                pago ->
                    repositorioPedidos.buscarPorId(pago.pedidoId()).stream()
                        .map(pedido -> new PagoSinTransaccion(pago, pedido)))
            .toList());
  }
}
