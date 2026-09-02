package co.tecnosport.api.application.carrito;

import java.util.UUID;

/** {@code usuarioId} nulo: carrito anónimo. */
public record CrearCarritoComando(UUID usuarioId) {}
