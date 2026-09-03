package co.tecnosport.api.bootstrap.pago;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code intervaloMinutos} también se lee directamente en {@code TareaConciliacionWompi.conciliar}
 * vía {@code @Scheduled(fixedDelayString = "${...}")} — Spring resuelve ese placeholder contra el
 * Environment, no contra este bean; los dos leen la misma llave de {@code application.yml}, con el
 * mismo efecto.
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
