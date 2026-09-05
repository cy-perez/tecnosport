package co.tecnosport.api.application.compartido;

import java.time.Duration;
import java.time.Instant;

/**
 * Límite de intentos por IP y por cuenta (docs/08-seguridad-legal.md). Puerto técnico, no de
 * negocio — mismo criterio que {@link RepositorioIdempotencia}: application es la única capa entre
 * presentation e infrastructure, así que el puerto vive aquí aunque no haya una regla de dominio
 * detrás.
 */
public interface LimitadorDeIntentos {

  /**
   * {@code true} si este intento se cuenta y está permitido; {@code false} si ya se llegó al máximo
   * dentro de la ventana vigente. Sin bloqueo pesimista a propósito: a diferencia del inventario,
   * una carrera aquí en el peor caso deja pasar uno o dos intentos de más bajo concurrencia alta —
   * no hay dinero ni existencia en juego.
   */
  boolean permitir(String clave, int maximoIntentos, Duration ventana, Instant ahora);
}
