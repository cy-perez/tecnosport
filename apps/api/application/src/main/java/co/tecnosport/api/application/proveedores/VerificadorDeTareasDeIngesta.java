package co.tecnosport.api.application.proveedores;

/**
 * ¿La petición que pide procesar un tramo la mandó nuestra cola? La API de dev es pública ({@code
 * allUsers} puede invocarla), así que el endpoint interno no puede fiarse de quien llama: comprueba
 * el token que Cloud Tasks firma con la cuenta de servicio de las tareas (ADR-0080).
 */
public interface VerificadorDeTareasDeIngesta {

  /**
   * @param token lo que viene después de {@code Bearer }
   * @return si es un token vigente de la cuenta de las tareas, para esta audiencia
   */
  boolean esDeLaCola(String token);
}
