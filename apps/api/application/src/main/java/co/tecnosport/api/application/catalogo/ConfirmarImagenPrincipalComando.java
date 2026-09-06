package co.tecnosport.api.application.catalogo;

import java.util.UUID;

public record ConfirmarImagenPrincipalComando(
    UUID productoId, String objectKey, int ancho, int alto, String altEs, String altEn) {}
