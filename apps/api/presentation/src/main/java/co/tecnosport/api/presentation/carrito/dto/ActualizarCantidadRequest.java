package co.tecnosport.api.presentation.carrito.dto;

/** Sin validación propia: el rango (cantidad > 0) ya lo cubre el dominio. */
public record ActualizarCantidadRequest(int cantidad) {}
