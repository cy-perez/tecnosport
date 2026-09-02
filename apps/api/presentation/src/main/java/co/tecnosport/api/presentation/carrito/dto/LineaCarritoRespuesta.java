package co.tecnosport.api.presentation.carrito.dto;

import java.util.UUID;

public record LineaCarritoRespuesta(UUID id, UUID varianteId, int cantidad) {}
