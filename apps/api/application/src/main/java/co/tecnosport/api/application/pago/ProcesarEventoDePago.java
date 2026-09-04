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

    EstadoPago nuevoEstado = EstadosWompi.aEstadoPago(comando.estadoWompi());
    if (nuevoEstado == null) {
      return ResultadoEventoDePago.ESTADO_NO_SOPORTADO;
    }

    Instant ahora = reloj.ahora();
    EventoPago evento = new EventoPago(comando.checksum(), nuevoEstado, ahora);
    return AplicadorDeResultadoDePago.aplicar(
        pagoEncontrado.get(),
        evento,
        "webhook-wompi",
        repositorioPagos,
        repositorioPedidos,
        repositorioInventario);
  }
}
