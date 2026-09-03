package co.tecnosport.api.application.compartido;

import java.time.Instant;
import java.util.Optional;

/**
 * Idempotencia por {@code Idempotency-Key} (docs/03-api.md): todo POST que mueva dinero o
 * inventario la acepta, y un reintento con la misma llave devuelve la misma respuesta en vez de
 * repetir el efecto. Puerto técnico, no de negocio — mismo criterio que {@link Reloj}: application
 * es la única capa entre presentation e infrastructure, así que el puerto vive aquí aunque no haya
 * una regla de dominio detrás.
 *
 * <p>{@code reclamar} y {@code completar} son dos pasos separados a propósito: si solo se guardara
 * la respuesta al final, un proceso que muere después de comprometer el efecto de negocio pero
 * antes de guardar la respuesta dejaría la llave sin rastro, y un reintento repetiría el efecto —
 * el caso exacto que esto existe para evitar. Reclamar primero, en su propia transacción, cierra
 * esa ventana.
 */
public interface RepositorioIdempotencia {

  /** Vacío si no hay fila, si sigue pendiente, o si ya venció (24 horas). */
  Optional<RespuestaIdempotente> buscarCompletada(String llave, Instant ahora);

  /**
   * {@code true} si esta llamada reclamó la llave; {@code false} si ya existía una fila (pendiente
   * o completada) — quien llama decide qué hacer en ese caso, este puerto no arbitra concurrencia.
   */
  boolean reclamar(String llave, String metodo, String ruta, Instant ahora);

  void completar(String llave, RespuestaIdempotente respuesta, Instant ahora);

  /**
   * Borra la reclamación: la ejecución terminó en un error de servidor (no de negocio), y cachear
   * ese error dejaría a un cliente que reintenta de buena fe atascado 24 horas repitiendo algo que
   * sí podría funcionar la próxima vez.
   */
  void liberar(String llave);
}
