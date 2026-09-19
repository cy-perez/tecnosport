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

  /**
   * Devuelve un reclamo cuyo correo no salió, para que el siguiente ciclo lo vuelva a intentar. Sin
   * esto, un SMTP caído dejaba la marca puesta y ese aviso no volvía a armarse nunca: la bandeja
   * seguía llena y nadie se enteraba, que es exactamente lo contrario de lo que esta vigilancia
   * existe para hacer.
   *
   * <p><strong>Borra la fila, no la restaura a como estaba.</strong> Cuando el reclamo se ganó
   * re-armando uno anterior, el instante que había antes ya se perdió y no hay a qué volver — pero
   * tampoco hace falta: {@code avisado_en} solo se lee dentro de {@link #reclamarAviso}, así que
   * una fila ausente y una fila vieja producen lo mismo, que es avisar en el siguiente ciclo.
   */
  void liberarAviso(TipoDeRevision tipo, UUID referencia);
}
