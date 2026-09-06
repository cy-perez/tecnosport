package co.tecnosport.api.application.catalogo;

import java.util.UUID;

public record AbrirSetRotacionComando(
    UUID productoId,
    int fotogramas,
    String capturadoPor,
    String dispositivo,
    String versionAsistente) {}
