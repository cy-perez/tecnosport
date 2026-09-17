package co.tecnosport.api.application.envio;

import co.tecnosport.api.domain.envio.TipoDeRevision;
import java.time.Instant;
import java.util.UUID;

public interface RepositorioAvisosDeRevision {

  /**
   * Reclama el derecho a avisar de algo que lleva demasiado tiempo en la bandeja. Devuelve {@code
   * true} si lo ganó quien llama, {@code false} si ya se había avisado de esta misma novedad.
   *
   * <p><strong>Es una sola escritura condicional y atómica</strong>, mismo criterio que {@code
   * RepositorioPedidos.reclamarAvisoDePlazo}, y de eso depende que nadie reciba el mismo correo dos
   * veces: con más de una instancia corriendo, leer y después escribir deja a las dos leyendo "no
   * se ha avisado" y mandando las dos.
   *
   * <p>{@code novedad} es lo que hace que el aviso se vuelva a armar: si a una guía ya avisada le
   * llega un evento posterior, esto vuelve a ganar el reclamo. Sin ese parámetro, el primer aviso
   * sería el único para siempre y un paquete que empeora pasaría callado.
   */
  boolean reclamarAviso(TipoDeRevision tipo, UUID referencia, Instant novedad, Instant ahora);
}
