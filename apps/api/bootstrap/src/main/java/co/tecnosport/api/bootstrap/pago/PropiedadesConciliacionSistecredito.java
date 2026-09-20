package co.tecnosport.api.bootstrap.pago;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Gemela de {@link PropiedadesConciliacionWompi}; ver allí por qué el intervalo se lee dos veces.
 */
@ConfigurationProperties(prefix = "tecnosport.sistecredito.conciliacion")
public record PropiedadesConciliacionSistecredito(
    int intervaloMinutos, int antiguedadMinimaMinutos) {

  public PropiedadesConciliacionSistecredito {
    if (intervaloMinutos <= 0) {
      throw new IllegalStateException(
          "tecnosport.sistecredito.conciliacion.intervalo-minutos debe ser mayor que cero.");
    }
    if (antiguedadMinimaMinutos <= 0) {
      throw new IllegalStateException(
          "tecnosport.sistecredito.conciliacion.antiguedad-minima-minutos debe ser mayor que cero.");
    }
  }
}
