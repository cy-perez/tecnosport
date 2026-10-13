package co.tecnosport.api.domain.proveedores;

/**
 * Qué es un {@link PrecioAdicional}. La dice el extractor desde el 12 de octubre de 2026, para que
 * {@link ArmadorDeSets} pueda armar el set sin leer conceptos escritos a mano; los guardados antes
 * no la tienen.
 */
public enum ClaseDePrecioAdicional {
  /** Una prenda que acompaña a la del anuncio, con su precio: «Gorra $35.000», «Básica 48.000». */
  ACOMPANANTE,
  /**
   * La prenda con su acompañante, a un solo precio: «Set falda + básica $88.000», «Dúo $95.000».
   */
  COMBO,
  /** Un precio por cantidad: «Promo 4x200.000». No arma ningún set. */
  PROMOCION,
  /**
   * El de la prenda sola, cuando el borrador es el set: «Falda $49.900». No lo dice el extractor;
   * lo agrega {@link ArmadorDeSets} para que quien revisa lo siga viendo.
   */
  PRENDA_SOLA
}
