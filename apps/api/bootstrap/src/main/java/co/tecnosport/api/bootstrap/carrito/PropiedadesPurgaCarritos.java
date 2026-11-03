package co.tecnosport.api.bootstrap.carrito;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code intervaloMinutos} lo lee también {@code CatalogoDeTareasProgramadas}, de {@code
 * application.yml} y con la misma llave, y lo pasa al tick (ADR-0104).
 *
 * <p>{@code diasRetencion} son los 30 días de docs/02-modelo-datos.md.
 */
@ConfigurationProperties(prefix = "tecnosport.carrito.purga")
public record PropiedadesPurgaCarritos(int intervaloMinutos, int diasRetencion) {

  public PropiedadesPurgaCarritos {
    if (intervaloMinutos <= 0) {
      throw new IllegalStateException(
          "tecnosport.carrito.purga.intervalo-minutos debe ser mayor que cero.");
    }
    if (diasRetencion <= 0) {
      throw new IllegalStateException(
          "tecnosport.carrito.purga.dias-retencion debe ser mayor que cero: con cero se borraría"
              + " el carrito que alguien está usando en este momento.");
    }
  }
}
