package co.tecnosport.api.application.pago;

import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.inventario.RepositorioInventario;
import co.tecnosport.api.application.pedido.RepositorioPedidos;
import co.tecnosport.api.domain.pago.EstadoPago;
import co.tecnosport.api.domain.pago.EventoPago;
import co.tecnosport.api.domain.pago.Pago;
import co.tecnosport.api.domain.pago.ReferenciaPago;
import co.tecnosport.api.domain.pedido.ProveedorDePago;
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

    // PRIMERO lo local, y solo despues la pasarela. Este endpoint es publico y anonimo: consultar
    // antes de mirar si la referencia siquiera existe convertia cada peticion inventada en una
    // llamada a Sistecredito con nuestras credenciales productivas, gratis para quien la mandara.
    // Salir aqui no debilita el contraste en nada: si no hay pago local, no habia nada que aplicar.
    Pago pago = pagoDeLaNotificacion(comando).orElse(null);
    if (pago == null) {
      return ResultadoNotificacionSistecredito.PAGO_NO_ENCONTRADO;
    }

    // Un pago ya resuelto no admite mas transiciones. Sin esto, un segundo estado terminal
    // --`Rejected` y despues `Expired`, que son dos ids de evento distintos con el mismo
    // `EstadoPago`-- reventaba con una excepcion de dominio que salia como 422, justo lo que el
    // controlador promete no devolver nunca. La pasarela habria reintentado en bucle.
    if (pago.estado() != EstadoPago.PENDIENTE) {
      return ResultadoNotificacionSistecredito.YA_PROCESADO;
    }

    Optional<TransaccionSistecredito> consultada = pasarela.consultar(comando.idTransaccion());
    if (consultada.isEmpty()) {
      return ResultadoNotificacionSistecredito.NO_SE_PUDO_VERIFICAR;
    }
    TransaccionSistecredito verdad = consultada.get();
    if (!coincide(verdad, comando) || !esDeEstePago(pago, verdad)) {
      return ResultadoNotificacionSistecredito.DISCREPANCIA_CON_LA_PASARELA;
    }

    EstadoPago estado = EstadosSistecredito.aEstadoPago(verdad.estado());
    if (estado == null) {
      return ResultadoNotificacionSistecredito.ESTADO_NO_SOPORTADO;
    }
    if (estado == EstadoPago.APROBADO && !elMontoCuadra(pago, verdad)) {
      return ResultadoNotificacionSistecredito.MONTO_NO_COINCIDE;
    }

    // El id del evento se compone porque la pasarela no le da uno: la notificacion trae la misma
    // estructura que la consulta y ahi no hay identificador de mensaje (G-SCL-21 3.3). Transaccion
    // mas estado es idempotente de verdad para este flujo --una transaccion pasa por cada estado
    // una vez-- y desduplica exactamente lo que hay que desduplicar: la misma notificacion
    // repetida.
    EventoPago evento = new EventoPago(verdad.id() + ":" + verdad.estado(), estado, reloj.ahora());

    return traducir(
        AplicadorDeResultadoDePago.aplicar(
            pago,
            evento,
            // Sistecredito es el medio: no hay una lista donde el comprador vuelva a elegir, como
            // en el checkout hospedado de Wompi. Se registra igual, para que el dato exista con la
            // misma forma en los dos caminos.
            "SISTECREDITO",
            "sistecredito",
            repositorioPagos,
            repositorioPedidos,
            repositorioInventario));
  }

  /**
   * El pago que esta notificacion dice tocar, buscado por la referencia que trae el cuerpo.
   *
   * <p>Se usa el dato de entrada <b>solo para decidir si vale la pena preguntar</b>; lo que se
   * aplica despues sale de la consulta. Si la notificacion no trae factura --la guia la marca
   * opcional-- no hay por donde buscar y se pregunta igual, que es el camino raro.
   */
  private Optional<Pago> pagoDeLaNotificacion(ProcesarNotificacionSistecreditoComando comando) {
    if (comando.referencia() != null && !comando.referencia().isBlank()) {
      return repositorioPagos.buscarPorReferencia(new ReferenciaPago(comando.referencia()));
    }
    return pasarela
        .consultar(comando.idTransaccion())
        .map(TransaccionSistecredito::referencia)
        .filter(referencia -> referencia != null && !referencia.isBlank())
        .flatMap(
            referencia -> repositorioPagos.buscarPorReferencia(new ReferenciaPago(referencia)));
  }

  private boolean coincide(
      TransaccionSistecredito verdad, ProcesarNotificacionSistecreditoComando comando) {
    if (!verdad.id().equals(comando.idTransaccion())) {
      return false;
    }
    if (comando.estado() != null && !verdad.estado().equalsIgnoreCase(comando.estado())) {
      return false;
    }
    // La factura solo se compara si la notificacion la trajo: la guia la marca como opcional
    // --"invoice (de haber sido enviada)"-- y nosotros siempre la mandamos, pero exigirla aqui
    // haria que una notificacion legitima sin ese campo se descartara.
    return comando.referencia() == null
        || comando.referencia().isBlank()
        || comando.referencia().equals(verdad.referencia());
  }

  /**
   * Dos comprobaciones que hoy no puede burlar nadie y que igual se hacen, porque lo que las
   * mantiene cerradas es accidental: que la referencia se derive del numero de pedido.
   *
   * <p>La primera es que el pago sea de Sistecredito --las dos conciliaciones ya filtran por
   * proveedor y esto no lo hacia--. La segunda es que el id de transaccion sea el que ese pago
   * guardo: decia {@code orElse(true)}, o sea "si no tengo id guardado, acepta cualquiera", y un
   * pago de Wompi cuyo id nunca se registro --el comprador que cierra la pestana, caso
   * documentado-- cumplia las dos condiciones.
   */
  private boolean esDeEstePago(Pago pago, TransaccionSistecredito verdad) {
    if (pago.metodoPago().pasarela() != ProveedorDePago.SISTECREDITO) {
      return false;
    }
    return pago.idTransaccionPasarela().map(verdad.id()::equals).orElse(false);
  }

  /**
   * De ida el monto lo pone el servidor (regla dura #7); de vuelta no habia nada que lo comprobara.
   * Wompi trae una firma de integridad sobre referencia, monto y moneda; aqui el contraste que
   * propone la guia no mira el valor, asi que un credito aprobado por menos de lo pedido --un cupo
   * tope, que es justo lo que hace un prestamista-- se aplicaria como pago completo y la diferencia
   * seria perdida invisible.
   *
   * <p>Si la pasarela no manda el valor, no se bloquea: negarse a aplicar un pago aprobado por un
   * campo que quiza nunca venga seria peor. Queda pendiente confirmarlo con el primer credito real
   * (docs/11-pagos-y-envios.md).
   */
  private boolean elMontoCuadra(Pago pago, TransaccionSistecredito verdad) {
    if (verdad.valor() == null) {
      return true;
    }
    return pago.monto().valor().longValueExact() == verdad.valor();
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
