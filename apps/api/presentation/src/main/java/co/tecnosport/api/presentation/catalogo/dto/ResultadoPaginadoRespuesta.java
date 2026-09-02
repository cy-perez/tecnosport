package co.tecnosport.api.presentation.catalogo.dto;

import java.util.List;

public record ResultadoPaginadoRespuesta<T>(List<T> items, String cursorSiguiente) {}
