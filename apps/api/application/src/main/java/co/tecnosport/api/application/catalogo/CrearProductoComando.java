package co.tecnosport.api.application.catalogo;

import java.util.UUID;

public record CrearProductoComando(String nombre, String descripcion, UUID marcaId, UUID categoriaId) {}
