package co.tecnosport.api.application.catalogo;

import co.tecnosport.api.domain.catalogo.ImagenProducto;

/**
 * La imagen que quedó como principal, y qué pasó con las que ocupaban su lugar en el bucket.
 *
 * <p>{@code limpiezaFallida} no es un error de la operación: para cuando se limpia, la imagen ya
 * está guardada y la ficha ya la muestra. Se reporta para que quien llame lo registre —
 * almacenamiento que no se reclamó es plata, y callarlo sería peor que un log— pero no convierte
 * una confirmación exitosa en un fallo.
 */
public record ConfirmacionDeImagenPrincipal(
    ImagenProducto imagen, int objetosAnterioresBorrados, boolean limpiezaFallida) {}
