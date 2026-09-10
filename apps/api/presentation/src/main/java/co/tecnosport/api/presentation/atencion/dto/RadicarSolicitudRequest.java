package co.tecnosport.api.presentation.atencion.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * {@code recibidaEn} lo escribe quien radica mirando la fecha del correo o del mensaje. Nulo
 * significa "llego ahora", que es el caso de quien radica en el momento; ponerlo por omision en
 * "ahora" sin poder cambiarlo convertiria el registro tardio en una forma de no incumplir nunca.
 */
public record RadicarSolicitudRequest(
    String tipo, String correo, UUID pedidoId, Instant recibidaEn, String asunto) {}
