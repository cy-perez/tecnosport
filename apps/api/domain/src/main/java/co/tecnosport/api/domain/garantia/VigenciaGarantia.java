package co.tecnosport.api.domain.garantia;

/**
 * Si el producto sigue amparado por la garantía legal cuando se reclama.
 *
 * <p>Tres valores y no dos, por el mismo motivo que {@code VerdictoPlazo} en el retracto: hay un
 * dato que este proyecto todavía no tiene. Los términos publicados dicen que para teléfonos
 * celulares aplica la garantía del fabricante, y ese plazo está marcado como pendiente ({@code
 * [[GARANTÍA DE CELULARES]]}). Sin él, el sistema no puede afirmar que un celular está fuera de
 * término — y afirmarlo sería negarle un derecho a alguien que quizá lo tiene.
 *
 * <p>{@code INDETERMINADA} nunca bloquea: quien decide es una persona, con este dato delante.
 */
public enum VigenciaGarantia {
  /** Dentro del término aplicable. */
  CUBIERTA,
  /** Fuera del término, y el término de esa categoría se conoce. */
  FUERA_DE_TERMINO,
  /** No se conoce el término de esa categoría, así que no se puede afirmar ninguna de las dos. */
  INDETERMINADA
}
