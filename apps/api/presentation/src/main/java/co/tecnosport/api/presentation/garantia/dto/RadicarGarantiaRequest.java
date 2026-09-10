package co.tecnosport.api.presentation.garantia.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * {@code recibidaEn} es la fecha del correo o el mensaje en que el comprador reclamo: de ella
 * cuelga el plazo de respuesta, igual que en cualquier otra solicitud. Nulo significa "llego
 * ahora".
 */
public record RadicarGarantiaRequest(
    UUID varianteId, Instant recibidaEn, String descripcionDelFallo) {}
