package co.tecnosport.api.application.catalogo;

import java.util.List;
import java.util.UUID;

/**
 * {@code hash} es el SHA-256 del archivo que el panel acaba de subir, calculado en el navegador
 * sobre los bytes que mandó. El servidor no puede verificarlo sin descargarlos (ADR-0016), pero sí
 * exige que sea un hash bien formado.
 *
 * <p>{@code variantes} son los objetos ya subidos, uno por ancho. Puede ser uno solo: el panel sube
 * el archivo que una persona eligió y no tiene de dónde sacar otras resoluciones. La escalera
 * completa la manda el cargador del catálogo, que sí las tiene del procesamiento de estudio.
 *
 * <p>{@code alto} y {@code hash} son los de la variante mayor, que es la que el agregado toma como
 * base. {@code objectKeyVistaPrevia} es opcional: el JPEG para los previsualizadores de enlaces.
 */
public record ConfirmarImagenPrincipalComando(
    UUID productoId,
    List<VarianteSubida> variantes,
    String objectKeyVistaPrevia,
    int alto,
    String hash,
    String altEs,
    String altEn) {}
