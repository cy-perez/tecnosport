package co.tecnosport.api.bootstrap.pago;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code intervaloMinutos} también lo lee {@code CatalogoDeTareasProgramadas} lo lee de {@code
 * application.yml} con la misma llave y lo pasa al tick (ADR-0104). Los dos leen la misma llave,
 * con el mismo efecto.
 */
@ConfigurationProperties(prefix = "tecnosport.wompi.conciliacion")
public record PropiedadesConciliacionWompi(int intervaloMinutos, int antiguedadMinimaMinutos) {

  public PropiedadesConciliacionWompi {
    if (intervaloMinutos <= 0) {
      throw new IllegalStateException(
          "tecnosport.wompi.conciliacion.intervalo-minutos debe ser mayor que cero.");
    }
    if (antiguedadMinimaMinutos <= 0) {
      throw new IllegalStateException(
          "tecnosport.wompi.conciliacion.antiguedad-minima-minutos debe ser mayor que cero.");
    }
  }
}
