package co.tecnosport.api.presentation.catalogo.dto;

import java.util.Objects;

/**
 * El conteo físico, no la diferencia (adr/0049). {@code cantidadContada} es primitivo a propósito:
 * un conteo ausente no es un estado legítimo, y con {@code int} Jackson 3 rechaza el cuerpo
 * incompleto antes de que llegue a ninguna parte.
 *
 * <p>{@code motivo} es de tipo referencia, así que Jackson 3 <b>sí</b> lo deja llegar en nulo —lo
 * documenta apps/api/CLAUDE.md— y necesita su propia guarda aquí. Las cifras y el contenido del
 * motivo los valida {@code AjustarExistenciaComando}; esta guarda existe solo para que un cuerpo al
 * que le falta el campo no llegue con un nulo hasta la capa de aplicación.
 */
public record AjustarExistenciaPeticion(int cantidadContada, String motivo) {

  public AjustarExistenciaPeticion {
    Objects.requireNonNull(motivo, "El motivo del ajuste de existencia es obligatorio.");
  }
}
