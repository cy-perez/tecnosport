package co.tecnosport.api.application.catalogo;

import java.util.UUID;

/**
 * {@code hash} es el SHA-256 del archivo que el panel acaba de subir, calculado en el navegador
 * sobre los bytes que mandó. El servidor no puede verificarlo sin descargarlos (ADR-0016), pero sí
 * exige que sea un hash bien formado.
 */
public record ConfirmarImagenPrincipalComando(
    UUID productoId,
    String objectKey,
    int ancho,
    int alto,
    String hash,
    String altEs,
    String altEn) {}
