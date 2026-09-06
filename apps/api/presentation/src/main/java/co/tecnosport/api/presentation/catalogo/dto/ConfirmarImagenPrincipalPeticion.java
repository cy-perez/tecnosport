package co.tecnosport.api.presentation.catalogo.dto;

public record ConfirmarImagenPrincipalPeticion(
    String objectKey, int ancho, int alto, String altEs, String altEn) {}
