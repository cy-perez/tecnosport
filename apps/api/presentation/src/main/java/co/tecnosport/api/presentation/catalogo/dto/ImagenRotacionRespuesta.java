package co.tecnosport.api.presentation.catalogo.dto;

/** Un fotograma se publica en un solo ancho: el visor los pinta todos igual (ADR-0057). */
public record ImagenRotacionRespuesta(int orden, String url, int ancho, int alto) {}
