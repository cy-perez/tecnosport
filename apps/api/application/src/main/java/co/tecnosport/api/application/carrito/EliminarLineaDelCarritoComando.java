package co.tecnosport.api.application.carrito;

import java.util.UUID;

public record EliminarLineaDelCarritoComando(UUID carritoId, UUID lineaId) {}
