package co.tecnosport.api.application.catalogo;

/**
 * Un objeto ya subido y el ancho al que corresponde. Es lo que el cliente reporta al confirmar una
 * imagen: pidió una URL firmada por cada variante, las subió, y ahora dice qué ancho es cada una.
 *
 * <p><b>El ancho lo declara el cliente y el servidor no lo verifica</b>, igual que ya pasaba con el
 * ancho único de antes: comprobarlo exigiría descargar y decodificar los bytes, que es justo lo que
 * la subida directa evita (ADR-0016). Lo que sí verifica el servidor es que el objeto exista y que
 * pertenezca al producto; un ancho mentido deja un {@code srcset} con números equivocados, no una
 * imagen que no es.
 */
public record VarianteSubida(int ancho, String objectKey) {}
