package co.tecnosport.api.application.catalogo;

import java.util.UUID;

public record EditarProductoComando(
    UUID productoId, String nombre, String descripcion, UUID marcaId, UUID categoriaId) {}
