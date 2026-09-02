package co.tecnosport.api.presentation.catalogo.dto;

public record ImagenRespuesta(
    String url, String urlWebp, int ancho, int alto, String altEs, String altEn) {}
