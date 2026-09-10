package co.tecnosport.api.application.garantia;

import java.time.Instant;
import java.util.UUID;

/**
 * {@code recibidaEn} es la fecha del correo o del mensaje en que el comprador reclamó, igual que en
 * cualquier otra solicitud de atención: de ella cuelga el plazo de respuesta. Nulo significa "llegó
 * ahora".
 */
public record RadicarReclamacionGarantiaComando(
    UUID pedidoId, UUID varianteId, Instant recibidaEn, String descripcionDelFallo, String actor) {

  public RadicarReclamacionGarantiaComando {
    if (actor == null || actor.isBlank()) {
      throw new IllegalArgumentException("El actor no puede estar vacío.");
    }
  }
}
