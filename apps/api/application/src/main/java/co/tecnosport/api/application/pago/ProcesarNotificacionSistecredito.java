package co.tecnosport.api.application.pago;

import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.inventario.RepositorioInventario;
import co.tecnosport.api.application.pedido.RepositorioPedidos;
import co.tecnosport.api.domain.pago.EstadoPago;
import co.tecnosport.api.domain.pago.EventoPago;
import co.tecnosport.api.domain.pago.Pago;
import co.tecnosport.api.domain.pago.ReferenciaPago;
import java.util.Objects;
import java.util.Optional;

/**
 * Aplica una notificación de Sistecrédito, <b>después de contrastarla contra la pasarela</b>
 * ({@code adr/0048}).
 *
 * <p>La diferencia con {@code ProcesarEventoDePago} no es de estilo. El webhook de Wompi llega
 * firmado: se verifica el checksum y se le cree. Sistecrédito <b>no firma nada</b>, y su endpoint
 * de confirmación es público por necesidad —la pasarela tiene que poder llamarlo—. Un cuerpo JSON
 * que dice "aprobado" lo puede enviar cualquiera desde cualquier parte del mundo, así que creerle
 * sería regalar mercancía a quien conozca el formato.
 *
 * <p>Lo que la autentica es lo que la propia guía {@code G-ALI-08} propone y aquí es obligatorio:
 * consultar la transacción por su {@code _id} y comparar {@code _id}, {@code invoice} y {@code
 * transactionStatus}. Si no coinciden, o si no se pudo preguntar, <b>no se aplica nada</b> — la
 * conciliación programada recogerá el pago después. Fallar cerrado aquí cuesta un retraso de
 * minutos; fallar abierto cuesta el pedido.
 */
public final class ProcesarNotificacionSistecredito {

  private final RepositorioPagos repositorioPagos;
  private final RepositorioPedidos repositorioPedidos;
  private final RepositorioInventario repositorioInventario;
  private final PasarelaSistecredito pasarela;
  private final Reloj reloj;

  public ProcesarNotificacionSistecredito(
      RepositorioPagos repositorioPagos,
      RepositorioPedidos repositorioPedidos,
      RepositorioInventario repositorioInventario,
      PasarelaSistecredito pasarela,
      Reloj reloj) {
    this.repositorioPagos =
        Objects.requireNonNull(repositorioPagos, "El repositorio de pagos no puede ser nulo.");
    this.repositorioPedidos =
        Objects.requireNonNull(repositorioPedidos, "El repositorio de pedidos no puede ser nulo.");
    this.repositorioInventario =
        Objects.requireNonNull(
            repositorioInventario, "El repositorio de inventario no puede ser nulo.");
    this.pasarela = Objects.requireNonNull(pasarela, "La pasarela no puede ser nula.");
    this.reloj = Objects.requireNonNull(reloj, "El reloj no puede ser nulo.");
  }

  public ResultadoNotificacionSistecredito ejecutar(
      ProcesarNotificacionSistecreditoComando comando) {
    Objects.requireNonNull(comando, "El comando no puede ser nulo.");
    if (comando.idTransaccion() == null || comando.idTransaccion().isBlank()) {
      return ResultadoNotificacionSistecredito.NO_SE_PUDO_VERIFICAR;
    }

    Optional<TransaccionSistecredito> consultada = pasarela.consultar(comando.idTransaccion());
    if (consultada.isEmpty()) {
      return ResultadoNotificacionSistecredito.NO_SE_PUDO_VERIFICAR;
    }
    TransaccionSistecredito verdad = consultada.get();
    if (!coincide(verdad, comando)) {
      return ResultadoNotificacionSistecredito.DISCREPANCIA_CON_LA_PASARELA;
    }

    // A partir de aquí manda lo que dijo la consulta, no lo que decía la notificación. Aunque
    // acaben de compararse iguales, quedarse con el cuerpo de entrada sería dejar la puerta
    // abierta a que un cambio futuro en la comparación afloje sin que nadie lo note.
    Optional<Pago> encontrado =
        repositorioPagos.buscarPorReferencia(new ReferenciaPago(verdad.referencia()));
    if (encontrado.isEmpty()) {
      return ResultadoNotificacionSistecredito.PAGO_NO_ENCONTRADO;
    }
    Pago pago = encontrado.get();
    if (!esLaTransaccionDeEstePago(pago, verdad)) {
      return ResultadoNotificacionSistecredito.DISCREPANCIA_CON_LA_PASARELA;
    }

    EstadoPago estado = EstadosSistecredito.aEstadoPago(verdad.estado());
    if (estado == null) {
      return ResultadoNotificacionSistecredito.ESTADO_NO_SOPORTADO;
    }

    // El id del evento se compone porque la pasarela no le da uno: la notificación trae la misma
    // estructura que la consulta y ahí no hay identificador de mensaje (G-SCL-21 §3.3). Transacción
    // más estado es idempotente de verdad para este flujo —una transacción pasa por cada estado una
    // vez— y desduplica exactamente lo que hay que desduplicar: la misma notificación repetida.
    EventoPago evento = new EventoPago(verdad.id() + ":" + verdad.estado(), estado, reloj.ahora());

    return traducir(
        AplicadorDeResultadoDePago.aplicar(
            pago,
            evento,
            // Sistecrédito es el medio: no hay una lista donde el comprador vuelva a elegir, como
            // en el checkout hospedado de Wompi. Se registra igual, para que el dato exista con la
            // misma forma en los dos caminos.
            "SISTECREDITO",
            "sistecredito",
            repositorioPagos,
            repositorioPedidos,
            repositorioInventario));
  }

  private boolean coincide(
      TransaccionSistecredito verdad, ProcesarNotificacionSistecreditoComando comando) {
    if (!verdad.id().equals(comando.idTransaccion())) {
      return false;
    }
    if (comando.estado() != null && !verdad.estado().equalsIgnoreCase(comando.estado())) {
      return false;
    }
    // La factura solo se compara si la notificación la trajo: la guía la marca como opcional
    // —"invoice (de haber sido enviada)"— y nosotros siempre la mandamos, pero exigirla aquí haría
    // que una notificación legítima sin ese campo se descartara.
    return comando.referencia() == null
        || comando.referencia().isBlank()
        || comando.referencia().equals(verdad.referencia());
  }

  /**
   * La referencia que la pasarela devuelve es la que nosotros le dimos, así que ya identifica el
   * pago. Esto comprueba lo otro: que el id de transacción que estamos aplicando sea el que ese
   * pago guardó al crearse, y no el de otra transacción que casualmente cite la misma factura.
   */
  private boolean esLaTransaccionDeEstePago(Pago pago, TransaccionSistecredito verdad) {
    return pago.idTransaccionPasarela().map(verdad.id()::equals).orElse(true);
  }

  private ResultadoNotificacionSistecredito traducir(ResultadoEventoDePago resultado) {
    return switch (resultado) {
      case APLICADO -> ResultadoNotificacionSistecredito.APLICADO;
      case APLICADO_SIN_CONFIRMAR_INVENTARIO ->
          ResultadoNotificacionSistecredito.APLICADO_SIN_CONFIRMAR_INVENTARIO;
      case YA_PROCESADO -> ResultadoNotificacionSistecredito.YA_PROCESADO;
      case PAGO_NO_ENCONTRADO -> ResultadoNotificacionSistecredito.PAGO_NO_ENCONTRADO;
      case ESTADO_NO_SOPORTADO -> ResultadoNotificacionSistecredito.ESTADO_NO_SOPORTADO;
      // No puede llegar: aquí no hay firma que validar. Se nombra en vez de caer en un `default`
      // para que, si algún día el aplicador devuelve algo nuevo, esto no compile.
      case FIRMA_INVALIDA -> ResultadoNotificacionSistecredito.DISCREPANCIA_CON_LA_PASARELA;
    };
  }
}
