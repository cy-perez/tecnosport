package co.tecnosport.api.bootstrap.compartido;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Límite para {@code POST /api/v1/sugerencias}.
 *
 * <p>Estrecho, y por un motivo distinto al del seguimiento: aquí no hay nada que adivinar, lo que
 * hay es un formulario público que escribe en la base y encola un correo. El uso legítimo es
 * minúsculo —alguien escribe una sugerencia, y rara vez dos seguidas—, así que un tope bajo no le
 * estorba a nadie real y sí le estorba a un script.
 *
 * <p>Tiene variante por cuenta, a diferencia del seguimiento, y no es para frenar el aluvión: es
 * para que nadie use el buzón escribiendo el correo de otra persona una y otra vez. Sirve de poco
 * —el correo se teclea a mano— y aun así es lo mismo que hace {@code SolicitarRecuperacion} por la
 * misma razón.
 */
@ConfigurationProperties(prefix = "tecnosport.limite-intentos.sugerencias")
public record PropiedadesLimiteSugerencias(
    int ipMaximo, int ipMinutos, int cuentaMaximo, int cuentaMinutos) {

  public PropiedadesLimiteSugerencias {
    if (ipMaximo <= 0) {
      throw new IllegalStateException(
          "tecnosport.limite-intentos.sugerencias.ip-maximo debe ser mayor que cero.");
    }
    if (ipMinutos <= 0) {
      throw new IllegalStateException(
          "tecnosport.limite-intentos.sugerencias.ip-minutos debe ser mayor que cero.");
    }
    if (cuentaMaximo <= 0) {
      throw new IllegalStateException(
          "tecnosport.limite-intentos.sugerencias.cuenta-maximo debe ser mayor que cero.");
    }
    if (cuentaMinutos <= 0) {
      throw new IllegalStateException(
          "tecnosport.limite-intentos.sugerencias.cuenta-minutos debe ser mayor que cero.");
    }
  }
}
