package co.tecnosport.api.presentation.carrito.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CarritoRespuesta(
    UUID id, UUID usuarioId, List<LineaCarritoRespuesta> lineas, Instant creadoEn) {}
