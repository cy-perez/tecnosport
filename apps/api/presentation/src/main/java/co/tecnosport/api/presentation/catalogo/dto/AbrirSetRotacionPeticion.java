package co.tecnosport.api.presentation.catalogo.dto;

import java.util.UUID;

public record AbrirSetRotacionPeticion(
    UUID productoId, int fotogramas, String dispositivo, String versionAsistente) {}
