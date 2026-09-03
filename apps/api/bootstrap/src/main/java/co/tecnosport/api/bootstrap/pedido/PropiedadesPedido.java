package co.tecnosport.api.bootstrap.pedido;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code MINUTOS_RESERVA_INVENTARIO} y {@code TRANSFERENCIA_HORAS_VENCIMIENTO} de
 * docs/07-infra-gcp.md. Valida en el constructor en vez de con Bean Validation: no hay {@code
 * spring-boot-starter-validation} en el classpath todavía y esto no lo justifica.
 */
@ConfigurationProperties(prefix = "tecnosport.pedido")
public record PropiedadesPedido(int minutosReservaInventario, int horasVencimientoTransferencia) {

  public PropiedadesPedido {
    if (minutosReservaInventario <= 0) {
      throw new IllegalStateException(
          "tecnosport.pedido.minutos-reserva-inventario debe ser mayor que cero.");
    }
    if (horasVencimientoTransferencia <= 0) {
      throw new IllegalStateException(
          "tecnosport.pedido.horas-vencimiento-transferencia debe ser mayor que cero.");
    }
  }
}
