package co.tecnosport.api.application.pago;

import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.inventario.RepositorioInventario;
import co.tecnosport.api.application.pedido.RepositorioPedidos;
import co.tecnosport.api.domain.pago.EstadoPago;
import co.tecnosport.api.domain.pago.EventoPago;
import co.tecnosport.api.domain.pago.Pago;
import co.tecnosport.api.domain.pago.ReferenciaPago;
import co.tecnosport.api.domain.pedido.Pedido;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Aplica un evento de webhook de Wompi a su {@link Pago} y, si corresponde, propaga el resultado al
 * {@link Pedido} (docs/11-pagos-y-envios.md: "la verdad es el webhook firmado", nunca lo que trae
 * el navegador). Nunca lanza por un evento inválido, no encontrado o repetido — esas no son
 * excepcionales para un webhook, son resultados normales que {@link ResultadoEventoDePago} nombra;
 * quien llame decide qué registrar con cada uno.
 */
public final class ProcesarEventoDePago {

  private final RepositorioPagos repositorioPagos;
  private final RepositorioPedidos repositorioPedidos;
  private final RepositorioInventario repositorioInventario;
  private final PasarelaDePagos pasarelaDePagos;
  private final Reloj reloj;

  public ProcesarEventoDePago(
      RepositorioPagos repositorioPagos,
      RepositorioPedidos repositorioPedidos,
      RepositorioInventario repositorioInventario,
      PasarelaDePagos pasarelaDePagos,
      Reloj reloj) {
    this.repositorioPagos = Objects.requireNonNull(repositorioPagos);
    this.repositorioPedidos = Objects.requireNonNull(repositorioPedidos);
    this.repositorioInventario = Objects.requireNonNull(repositorioInventario);
    this.pasarelaDePagos = Objects.requireNonNull(pasarelaDePagos);
    this.reloj = Objects.requireNonNull(reloj);
  }

  public ResultadoEventoDePago ejecutar(ProcesarEventoDePagoComando comando) {
    Objects.requireNonNull(comando, "El comando no puede ser nulo.");

    boolean firmaValida =
        pasarelaDePagos.verificarFirmaEvento(
            comando.valoresPropiedadesFirma(), comando.timestampFirma(), comando.checksum());
    if (!firmaValida) {
      return ResultadoEventoDePago.FIRMA_INVALIDA;
    }

    Optional<Pago> pagoEncontrado =
        repositorioPagos.buscarPorReferencia(new ReferenciaPago(comando.referencia()));
    if (pagoEncontrado.isEmpty()) {
      return ResultadoEventoDePago.PAGO_NO_ENCONTRADO;
    }

    // Un pago ya resuelto no admite más transiciones, y sin esto el webhook contesta 422 a algo que
    // la pasarela va a reintentar. La desduplicación por `idEvento` no cubre este caso: el id es el
    // `checksum`, así que un webhook que llega tarde —después de que la conciliación resolvió el
    // pago con su propio id de evento— trae uno distinto, `aplicarEvento` lo ve nuevo, la máquina
    // de estados declara `APROBADO` sin salidas y salta `TransicionDePagoInvalidaException`, que
    // `ManejadorDeErrores` traduce a 422. El javadoc del controlador promete que el webhook siempre
    // responde 200; hoy era falso, y ante un no-2xx Wompi reintenta en bucle.
    //
    // Es la misma guarda que `ProcesarNotificacionSistecredito` recibió el 23 de septiembre, por
    // exactamente el mismo motivo. Se quedó sin portar al otro camino.
    if (pagoEncontrado.get().estado() != EstadoPago.PENDIENTE) {
      return ResultadoEventoDePago.YA_PROCESADO;
    }

    EstadoPago nuevoEstado = EstadosWompi.aEstadoPago(comando.estadoWompi());
    if (nuevoEstado == null) {
      return ResultadoEventoDePago.ESTADO_NO_SOPORTADO;
    }

    Instant ahora = reloj.ahora();
    EventoPago evento = new EventoPago(comando.checksum(), nuevoEstado, ahora);
    return AplicadorDeResultadoDePago.aplicar(
        pagoEncontrado.get(),
        evento,
        comando.medioWompi(),
        "webhook-wompi",
        repositorioPagos,
        repositorioPedidos,
        repositorioInventario);
  }
}
