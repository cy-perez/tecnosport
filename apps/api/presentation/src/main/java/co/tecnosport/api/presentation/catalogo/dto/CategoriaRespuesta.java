package co.tecnosport.api.presentation.catalogo.dto;

import java.util.UUID;

public record CategoriaRespuesta(UUID id, String nombre, String slug, String linea) {}
