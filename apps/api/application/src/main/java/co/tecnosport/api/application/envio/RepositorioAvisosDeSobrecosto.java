package co.tecnosport.api.application.envio;

import java.time.Instant;

public interface RepositorioAvisosDeSobrecosto {

  /**
   * Reclama el derecho a avisar de un cobro extra. Devuelve {@code true} si lo ganó quien llama,
   * {@code false} si de ese mismo cobro ya se había avisado.
   *
   * <p><strong>Una sola escritura condicional y atómica</strong>, mismo criterio que {@link
   * RepositorioAvisosDeRevision#reclamarAviso}: leer y después escribir deja a dos instancias
   * leyendo "no se ha avisado" y mandando las dos.
   *
   * <p>Y a diferencia de aquél, <strong>esto no se vuelve a ganar nunca</strong>. Un cobro extra no
   * tiene novedades posteriores que valga la pena repetir: cuando cambia lo que importa —el monto—
   * cambia la clave, y entonces es otro cobro para este propósito. Ver {@link
   * SobrecostoDeEnvio#clave()}.
   */
  boolean reclamarAviso(String clave, Instant ahora);

  /**
   * Devuelve un reclamo cuyo correo no salió, para que el siguiente ciclo lo vuelva a intentar.
   * Aquí importa más que en la bandeja: este es el aviso de que la transportadora nos cobró de más,
   * y con {@code do nothing} un reclamo perdido no se vuelve a ganar jamás — ese cobro quedaba sin
   * avisar para siempre y solo aparecía en el extracto.
   */
  void liberarAviso(String clave);
}
