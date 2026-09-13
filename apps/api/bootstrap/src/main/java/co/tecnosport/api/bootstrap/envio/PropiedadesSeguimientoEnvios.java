package co.tecnosport.api.bootstrap.envio;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cada cuánto corre la conciliación de envíos y desde cuándo se considera callado un envío ({@code
 * SKYDROPX_SEGUIMIENTO_*} de docs/07-infra-gcp.md).
 *
 * <p>{@code maximoPorCorrida} acota el lote: cada envío revisado es una llamada a un proveedor
 * limitado a dos peticiones por segundo, y la tarea corre dentro de una transacción. Con techo, el
 * tiempo que se retiene la conexión tiene techo; lo que no quepa espera a la vuelta siguiente.
 *
 * <p>La antigüedad mínima existe para no pisarle el turno al webhook: si la conciliación revisara
 * envíos de hace diez minutos, preguntaría por paquetes cuyo evento va en camino y gastaría cuota
 * del proveedor para llegar a la misma conclusión.
 */
@ConfigurationProperties(prefix = "tecnosport.skydropx.seguimiento")
public record PropiedadesSeguimientoEnvios(
    int intervaloMinutos, int antiguedadMinimaHoras, int maximoPorCorrida) {

  public PropiedadesSeguimientoEnvios {
    if (intervaloMinutos <= 0) {
      throw new IllegalStateException(
          "tecnosport.skydropx.seguimiento.intervalo-minutos debe ser mayor que cero.");
    }
    if (antiguedadMinimaHoras <= 0) {
      throw new IllegalStateException(
          "tecnosport.skydropx.seguimiento.antiguedad-minima-horas debe ser mayor que cero.");
    }
    if (maximoPorCorrida <= 0) {
      throw new IllegalStateException(
          "tecnosport.skydropx.seguimiento.maximo-por-corrida debe ser mayor que cero.");
    }
  }
}
