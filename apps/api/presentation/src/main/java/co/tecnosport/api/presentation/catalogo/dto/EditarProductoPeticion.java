package co.tecnosport.api.presentation.catalogo.dto;

import java.util.UUID;

public record EditarProductoPeticion(
    String nombre, String descripcion, UUID marcaId, UUID categoriaId) {}
