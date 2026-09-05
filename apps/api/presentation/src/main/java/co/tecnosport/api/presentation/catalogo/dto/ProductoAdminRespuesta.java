package co.tecnosport.api.presentation.catalogo.dto;

import java.util.UUID;

public record ProductoAdminRespuesta(
    UUID id,
    String nombre,
    String slug,
    String estado,
    MarcaRespuesta marca,
    CategoriaRespuesta categoria,
    String imagenPrincipalUrl,
    int totalVariantes) {}
