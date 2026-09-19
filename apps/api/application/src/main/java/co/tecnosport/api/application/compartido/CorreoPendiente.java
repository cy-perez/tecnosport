package co.tecnosport.api.application.compartido;

import co.tecnosport.api.domain.compartido.CorreoElectronico;
import java.util.Objects;
import java.util.UUID;

/**
 * Un correo que está en la bandeja de salida esperando a que alguien lo mande.
 *
 * <p>{@code intentos} son los que ya se hicieron, no los que quedan: lo lleva la fila porque el
 * tope y el espaciado se deciden con él, y porque un correo que se rindió tiene que poder
 * distinguirse de uno que acaba de entrar.
 *
 * <p><b>No es una entidad del dominio y no debe volverse una.</b> La bandeja es un mecanismo
 * técnico —como {@code RepositorioIdempotencia} o {@code Reloj}—, no una regla de negocio de
 * TecnoSport: que un correo se reintente cinco veces no es algo que un comprador ni la Ley 1480
 * puedan observar.
 */
public record CorreoPendiente(
    UUID id, CorreoElectronico destinatario, String asunto, String cuerpoHtml, int intentos) {

  public CorreoPendiente {
    Objects.requireNonNull(id, "El id del correo pendiente no puede ser nulo.");
    Objects.requireNonNull(destinatario, "El destinatario no puede ser nulo.");
    Objects.requireNonNull(asunto, "El asunto no puede ser nulo.");
    Objects.requireNonNull(cuerpoHtml, "El cuerpo del correo no puede ser nulo.");
    if (intentos < 0) {
      throw new IllegalArgumentException("Los intentos no pueden ser negativos.");
    }
  }
}
