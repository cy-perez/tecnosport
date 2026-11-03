package co.tecnosport.api.application.compartido;

/**
 * ¿El tick lo mandó nuestro Cloud Scheduler? La API es pública, así que el endpoint interno no
 * puede fiarse de quien llama: comprueba el token que Cloud Scheduler firma con la cuenta de las
 * tareas programadas (ADR-0104).
 *
 * <p>Es un puerto aparte del de la ingesta, con otra cuenta, a propósito: el token de quien encola
 * un tramo no dispara la conciliación de pagos, ni al revés.
 */
public interface VerificadorDeTareasProgramadas {

  /**
   * @param token lo que viene después de {@code Bearer }
   * @return si es un token vigente de la cuenta del programador, para esta audiencia
   */
  boolean esDelProgramador(String token);
}
