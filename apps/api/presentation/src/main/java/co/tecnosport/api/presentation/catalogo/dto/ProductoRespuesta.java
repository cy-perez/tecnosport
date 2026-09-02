package co.tecnosport.api.presentation.catalogo.dto;

import java.util.List;

public record ProductoRespuesta(
    String slug,
    String nombre,
    String descripcion,
    MarcaRespuesta marca,
    CategoriaRespuesta categoria,
    ImagenRespuesta imagenPrincipal,
    List<ImagenRespuesta> galeria,
    RotacionRespuesta rotacion,
    List<VarianteRespuesta> variantes) {}
