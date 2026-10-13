package co.tecnosport.api.domain.proveedores;

/**
 * Lo que el extractor escribe de una prenda vendida con su acompañante —la falda con la blusa, la
 * camiseta con la gorra—, aparte de lo que escribe de la prenda sola. Lo pide siempre que el
 * anuncio ofrezca las dos juntas, y solo lo usa {@link ArmadorDeSets} con un proveedor que vende
 * sets: el modelo no sabe de quién es el mensaje, y así no hace falta que lo sepa.
 *
 * <p>El título no va aquí: lo compone {@link TituloDeSet} con el de la prenda, que ya pasó por las
 * reglas de réplica y de polo.
 *
 * @param descripcion el texto de la ficha del set, que dice que la blusa o la gorra es la de la
 *     foto
 * @param altEn el nombre del set en inglés, para el texto alternativo de las fotos
 */
public record TextosDelSet(String descripcion, String altEn) {

  public TextosDelSet {
    descripcion = enBlancoEsNulo(NombreDeCategoria.corregir(descripcion));
    altEn = enBlancoEsNulo(altEn);
  }

  private static String enBlancoEsNulo(String valor) {
    return valor == null || valor.isBlank() ? null : valor.strip();
  }
}
