package co.tecnosport.api.application.pago;

import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.pedido.RepositorioPedidos;
import co.tecnosport.api.domain.pago.EstadoPago;
import co.tecnosport.api.domain.pago.EventoPago;
import co.tecnosport.api.domain.pago.Pago;
import co.tecnosport.api.domain.pago.ReferenciaPago;
import co.tecnosport.api.domain.pedido.EstadoPedido;
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
 *
 * <p>{@code VOIDED} (una transacción aprobada que luego se anula) no está contemplado: el grafo de
 * {@link EstadoPago} de este caso de uso solo sale de {@code PENDIENTE}. Anulaciones y reembolsos
 * son un caso de negocio aparte, fuera de este alcance a propósito.
 */
public final class ProcesarEventoDePago {

  private final RepositorioPagos repositorioPagos;
  private final RepositorioPedidos repositorioPedidos;
  private final PasarelaDePagos pasarelaDePagos;
  private final Reloj reloj;

  public ProcesarEventoDePago(
      RepositorioPagos repositorioPagos,
      RepositorioPedidos repositorioPedidos,
      PasarelaDePagos pasarelaDePagos,
      Reloj reloj) {
    this.repositorioPagos = Objects.requireNonNull(repositorioPagos);
    this.repositorioPedidos = Objects.requireNonNull(repositorioPedidos);
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
    Pago pago = pagoEncontrado.get();

    EstadoPago nuevoEstado = aEstadoPago(comando.estadoWompi());
    if (nuevoEstado == null) {
      return ResultadoEventoDePago.ESTADO_NO_SOPORTADO;
    }

    Instant ahora = reloj.ahora();
    boolean aplicado = pago.aplicarEvento(new EventoPago(comando.checksum(), nuevoEstado, ahora));
    if (!aplicado) {
      return ResultadoEventoDePago.YA_PROCESADO;
    }
    repositorioPagos.guardar(pago);

    propagarAlPedido(pago, nuevoEstado, ahora);
    return ResultadoEventoDePago.APLICADO;
  }

  private void propagarAlPedido(Pago pago, EstadoPago nuevoEstadoPago, Instant ahora) {
    EstadoPedido siguienteEstadoPedido =
        switch (nuevoEstadoPago) {
          case APROBADO -> EstadoPedido.PAGADO;
          case RECHAZADO, ERROR -> EstadoPedido.PAGO_FALLIDO;
          case PENDIENTE -> null;
        };
    if (siguienteEstadoPedido == null) {
      return;
    }
    Pedido pedido = repositorioPedidos.buscarPorId(pago.pedidoId()).orElse(null);
    // Un pedido ya resuelto por otro intento de pago no se toca: EstadoPedido ya rechazaría la
    // transición, pero comprobarlo antes evita depender de esa excepción como control de flujo.
    if (pedido != null && pedido.estado() == EstadoPedido.PAGO_PENDIENTE) {
      pedido.transicionar(
          siguienteEstadoPedido, "webhook-wompi", "evento de pago: " + nuevoEstadoPago, ahora);
      repositorioPedidos.guardar(pedido);
    }
  }

  private EstadoPago aEstadoPago(String estadoWompi) {
    return switch (estadoWompi) {
      case "APPROVED" -> EstadoPago.APROBADO;
      case "DECLINED" -> EstadoPago.RECHAZADO;
      case "ERROR" -> EstadoPago.ERROR;
      default -> null; // VOIDED u otro estado no contemplado (ver javadoc de la clase).
    };
  }
}
