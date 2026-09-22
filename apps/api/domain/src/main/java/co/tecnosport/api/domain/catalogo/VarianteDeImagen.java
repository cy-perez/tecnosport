package co.tecnosport.api.domain.catalogo;

/**
 * Una de las resoluciones en que se publicó una imagen: el mismo encuadre, el mismo formato y otro
 * ancho.
 *
 * <p><strong>Lleva su URL y no solo su ancho, a propósito.</strong> Las keys del almacén las
 * construye quien pide la subida, y esa forma no tiene por qué conocerla nadie más: si la variante
 * guardara únicamente el ancho, el mapeador de respuestas y el {@code IMAGE_LOADER} del frontend
 * tendrían que recomponer la URL a partir del patrón, cada uno por su cuenta, y mantenerlos
 * sincronizados a mano. Ese acoplamiento silencioso es exactamente el que produjo la columna {@code
 * url_webp}, que durante cuatro meses prometió un formato que nadie generaba.
 *
 * <p>El alto no se guarda: es el del encuadre, que no cambia entre variantes, y se deduce del alto
 * de la imagen y de la proporción. Guardarlo sería una tercera copia de lo mismo.
 */
public record VarianteDeImagen(int ancho, String url, long bytes) {

  public VarianteDeImagen {
    if (ancho <= 0) {
      throw new ImagenProductoInvalidaException("El ancho de una variante debe ser positivo.");
    }
    if (url == null || url.isBlank()) {
      throw new ImagenProductoInvalidaException("La URL de una variante no puede estar vacía.");
    }
    url = url.trim();
    if (bytes <= 0) {
      throw new ImagenProductoInvalidaException(
          "El tamaño en bytes de una variante debe ser positivo.");
    }
  }
}
