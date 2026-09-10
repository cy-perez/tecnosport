package co.tecnosport.api.application.retracto;

import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.Sku;
import co.tecnosport.api.domain.pedido.Direccion;
import co.tecnosport.api.domain.pedido.EstadoPedido;
import co.tecnosport.api.domain.pedido.LineaPedido;
import co.tecnosport.api.domain.pedido.MetodoPago;
import co.tecnosport.api.domain.pedido.NumeroPedido;
import co.tecnosport.api.domain.pedido.Pedido;
import co.tecnosport.api.domain.pedido.TipoEntrega;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

final class PedidosDePrueba {

  private PedidosDePrueba() {}

  static LineaPedido linea(UUID varianteId, UUID idReserva) {
    return new LineaPedido(
        UUID.randomUUID(),
        varianteId,
        new Sku("TS-CAM-AZ-M"),
        "Camiseta running Dry-Fit",
        1,
        Dinero.deCop(BigDecimal.valueOf(50_000)),
        new BigDecimal("0.19"),
        "https://cdn.tecnosport.co/img.webp",
        idReserva);
  }

  static Pedido entregado(MetodoPago metodoPago, LineaPedido linea, Instant entregadoEn) {
    Pedido pedido =
        Pedido.crear(
            NumeroPedido.de(2026, 1),
            null,
            new CorreoElectronico("cliente@tecnosport.co"),
            List.of(linea),
            TipoEntrega.ENVIO_A_DOMICILIO,
            new Direccion("05", "Antioquia", "05001", "Medellín", "Cra. 26C #38B-31", null),
            metodoPago,
            "cliente@tecnosport.co",
            entregadoEn.minusSeconds(86_400));
    if (metodoPago != MetodoPago.CONTRAENTREGA) {
      pedido.transicionar(EstadoPedido.PAGADO, "sistema", "pago aprobado", entregadoEn);
    }
    pedido.transicionar(EstadoPedido.EN_PREPARACION, "admin:1", "listo", entregadoEn);
    pedido.transicionar(EstadoPedido.DESPACHADO, "admin:1", "guía 123", entregadoEn);
    pedido.transicionar(EstadoPedido.ENTREGADO, "admin:1", "entregado", entregadoEn);
    return pedido;
  }

  static Pedido despachado(MetodoPago metodoPago, LineaPedido linea, Instant ahora) {
    Pedido pedido =
        Pedido.crear(
            NumeroPedido.de(2026, 2),
            null,
            new CorreoElectronico("cliente@tecnosport.co"),
            List.of(linea),
            TipoEntrega.ENVIO_A_DOMICILIO,
            new Direccion("05", "Antioquia", "05001", "Medellín", "Cra. 26C #38B-31", null),
            metodoPago,
            "cliente@tecnosport.co",
            ahora);
    if (metodoPago != MetodoPago.CONTRAENTREGA) {
      pedido.transicionar(EstadoPedido.PAGADO, "sistema", "pago aprobado", ahora);
    }
    pedido.transicionar(EstadoPedido.EN_PREPARACION, "admin:1", "listo", ahora);
    pedido.transicionar(EstadoPedido.DESPACHADO, "admin:1", "guía 123", ahora);
    return pedido;
  }
}
