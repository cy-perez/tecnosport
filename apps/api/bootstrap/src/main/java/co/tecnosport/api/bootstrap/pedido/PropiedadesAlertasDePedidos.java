package co.tecnosport.api.bootstrap.pedido;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Los umbrales de las alertas del panel de pedidos. Datos de operación, no de negocio: cuándo
 * conviene que alguien mire, no un plazo que se le prometa a nadie.
 *
 * <p>{@code pagoSinTransaccionHoras}: un pago de Wompi pendiente y sin id de transacción no lo
 * concilia nadie. Doce horas por omisión (decidido el 27/10/2026): de sobra para que llegue un
 * webhook o vuelva el navegador, y menos de un día para que una venta cobrada no se enfríe.
 */
@ConfigurationProperties(prefix = "tecnosport.alertas")
public record PropiedadesAlertasDePedidos(int pagoSinTransaccionHoras) {

  public PropiedadesAlertasDePedidos {
    if (pagoSinTransaccionHoras <= 0) {
      throw new IllegalStateException(
          "tecnosport.alertas.pago-sin-transaccion-horas debe ser mayor que cero.");
    }
  }
}
