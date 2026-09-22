package co.tecnosport.api.application.catalogo;

import java.util.List;
import java.util.UUID;

/**
 * Mismo contrato que {@link ConfirmarImagenPrincipalComando}: {@code hash} es el SHA-256 que el
 * navegador calculó sobre los bytes que subió, y el servidor no puede verificarlo sin descargarlos
 * (ADR-0016). Aquí además decide algo: dos imágenes con el mismo hash no entran a la misma galería.
 *
 * <p>No lleva {@code orden}. Lo pone el agregado, que es quien sabe cuál es el siguiente.
 */
public record AgregarImagenDeGaleriaComando(
    UUID productoId,
    List<VarianteSubida> variantes,
    String objectKeyVistaPrevia,
    int alto,
    String hash,
    String altEs,
    String altEn) {}
