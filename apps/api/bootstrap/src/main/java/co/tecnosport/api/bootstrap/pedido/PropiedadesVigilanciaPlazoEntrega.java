package co.tecnosport.api.bootstrap.pedido;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cada cuánto mira el vigilante si algún pedido pasó de los treinta días para entregarse.
 *
 * <p>El intervalo también lo lee {@code CatalogoDeTareasProgramadas} lo lee de {@code
 * application.yml} con la misma llave y lo pasa al tick (ADR-0104).
 *
 * <p>Doce horas de intervalo, y no quince minutos como Wompi: el plazo que vigila es de treinta
 * días calendario, así que enterarse medio día más tarde no le cuesta nada a nadie. Y desde
 * ADR-0104 un despliegue ya no reinicia la cuenta: la lleva la bitácora.
 */
@ConfigurationProperties(prefix = "tecnosport.pedido.vigilancia-plazo")
public record PropiedadesVigilanciaPlazoEntrega(int intervaloMinutos) {

  public PropiedadesVigilanciaPlazoEntrega {
    if (intervaloMinutos <= 0) {
      throw new IllegalStateException(
          "tecnosport.pedido.vigilancia-plazo.intervalo-minutos debe ser mayor que cero.");
    }
  }
}
