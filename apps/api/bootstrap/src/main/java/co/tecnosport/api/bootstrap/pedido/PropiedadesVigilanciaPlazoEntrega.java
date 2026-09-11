package co.tecnosport.api.bootstrap.pedido;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cada cuánto mira el vigilante si algún pedido pasó de los treinta días para entregarse.
 *
 * <p>{@code intervaloHoras} también se lee directamente en {@code
 * TareaAvisoDePlazoDeEntrega.vigilar} vía {@code @Scheduled(fixedDelayString = "${...}")} — Spring
 * resuelve ese placeholder contra el Environment, no contra este bean; los dos leen la misma llave
 * de {@code application.yml}, con el mismo efecto. Mismo arreglo que {@code
 * PropiedadesConciliacionWompi}.
 *
 * <p>Doce horas por omisión, y no quince minutos como la conciliación de Wompi: el plazo que vigila
 * es de treinta días calendario, así que enterarse medio día más tarde no le cuesta nada a nadie, y
 * en cambio evita un barrido cada cuarto de hora que casi siempre no encontrará nada.
 */
@ConfigurationProperties(prefix = "tecnosport.pedido.vigilancia-plazo")
public record PropiedadesVigilanciaPlazoEntrega(int intervaloHoras) {

  public PropiedadesVigilanciaPlazoEntrega {
    if (intervaloHoras <= 0) {
      throw new IllegalStateException(
          "tecnosport.pedido.vigilancia-plazo.intervalo-horas debe ser mayor que cero.");
    }
  }
}
