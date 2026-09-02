package co.tecnosport.api.application.carrito;

import java.util.UUID;

public record AgregarLineaAlCarritoComando(UUID carritoId, UUID varianteId, int cantidad) {}
