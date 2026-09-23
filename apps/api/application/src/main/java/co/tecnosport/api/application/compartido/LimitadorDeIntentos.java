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

  /**
   * Borra el conteo de una llave. La llaman los casos de uso que verifican un secreto —{@code
   * IniciarSesion} y {@code CambiarClave}— en cuanto ese secreto resulta correcto, para que el
   * límite cuente intentos <b>fallidos seguidos</b> y no intentos a secas.
   *
   * <p>Hace falta porque {@link #permitir} cuenta y comprueba en la misma sentencia atómica, y eso
   * es a propósito: preguntar primero y contar después reabre la carrera que esa sentencia cierra.
   * El precio es que el acierto también suma, y el precio se paga aquí, después de saber que
   * acertó.
   *
   * <p>No la llaman {@code RegistrarUsuario}, {@code SolicitarRecuperacion} ni {@code CrearPedido}:
   * ahí un "acierto" no prueba que quien llama conozca ningún secreto, así que borrar el conteo
   * dejaría el límite sin efecto — que es justo lo que esos tres frenan.
   *
   * <p>Idempotente: borrar un conteo que no existe no hace nada.
   */
  void olvidar(String clave);
}
