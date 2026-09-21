package co.tecnosport.api.application.catalogo;

import co.tecnosport.api.domain.catalogo.ImagenProducto;

/**
 * Lo que quedó de quitar una imagen de la galería: cuál era —quien llama la devuelve al panel— y
 * qué pasó con su objeto en el bucket.
 *
 * <p>{@code objetoBorrado} en {@code false} sin {@code limpiezaFallida} no es un error: es una
 * imagen que no vivía en nuestro almacén (las de picsum del catálogo sembrado) o un objeto que ya
 * no estaba. {@code limpiezaFallida} sí merece un registro: la fila se fue y el archivo se quedó.
 */
public record ImagenDeGaleriaQuitada(
    ImagenProducto imagen, boolean objetoBorrado, boolean limpiezaFallida) {}
