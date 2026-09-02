package co.tecnosport.api.application.carrito;

import java.util.UUID;

public record ActualizarCantidadDeLineaComando(UUID carritoId, UUID lineaId, int cantidad) {}
