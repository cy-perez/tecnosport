package co.tecnosport.api.domain.proveedores;

/**
 * En qué orden manda un proveedor las fotos y el texto con el precio de un mismo producto.
 *
 * <p>Solo decide un empate. Android exporta la hora sin segundos, y cuando una foto queda en el
 * mismo minuto que dos precios —el del producto anterior y el del siguiente— la distancia no dice
 * de cuál es. Lo dice la costumbre del proveedor, y no hay una sola: en las exportaciones del 5 de
 * octubre de 2026, Imperio Wicho y D'Osman mandan el álbum y después el texto, y La Riverah manda
 * el texto con un «👇👇👇» y después las fotos. Con la regla única de antes, el jean «efecto cuero
 * negro» de La Riverah salió sin fotos y la suya cayó en el jean blanco del mismo minuto.
 *
 * <p>Fuera del empate manda la cercanía, sea cual sea el orden: aplicar «siempre al anterior» a
 * Imperio Wicho, probado contra su exportación real, dejaba dos publicaciones sin foto.
 */
public enum OrdenDePublicacion {
  /** El álbum sale antes que su texto: en el empate, la foto es del precio de después. */
  FOTOS_PRIMERO,
  /** El texto sale antes que sus fotos: en el empate, la foto es del precio de antes. */
  TEXTO_PRIMERO
}
