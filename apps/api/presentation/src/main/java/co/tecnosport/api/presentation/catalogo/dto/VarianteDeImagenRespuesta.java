package co.tecnosport.api.presentation.catalogo.dto;

/**
 * Una resolución publicada de la imagen. La lista completa es lo que el frontend necesita para
 * armar el {@code srcset}: el ancho va en el descriptor y la URL la resuelve el {@code
 * IMAGE_LOADER} de Angular, que la recibe como dato y no la deduce de la forma de la key.
 *
 * <p>Sin los bytes: el navegador no los usa para elegir y publicarlos solo daría de qué hablar a
 * quien mire la respuesta.
 */
public record VarianteDeImagenRespuesta(int ancho, String url) {}
