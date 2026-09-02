package co.tecnosport.api.presentation.catalogo.dto;

import java.util.List;

public record RotacionRespuesta(int fotogramas, List<ImagenRotacionRespuesta> imagenes) {}
