package co.tecnosport.api.presentation.catalogo.dto;

/** Sin {@code orden}: lo decide el agregado, que es quien sabe cuál es el siguiente. */
public record AgregarImagenDeGaleriaPeticion(
    String objectKey, int ancho, int alto, String hash, String altEs, String altEn) {}
