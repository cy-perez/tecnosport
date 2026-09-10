package co.tecnosport.api.domain.garantia;

/**
 * Si el producto sigue amparado por la garantía legal cuando se reclama.
 *
 * <p>Tres valores y no dos, por el mismo motivo que {@code VerdictoPlazo} en el retracto: puede
 * faltar el término de una categoría, y entonces el sistema no puede afirmar que algo está fuera de
 * término — afirmarlo sería negarle un derecho a alguien que quizá lo tiene. Hoy no falta ninguno:
 * los celulares ocuparon este estado mientras se creyó que su término era un dato del negocio, y
 * resultó ser el año legal de cualquier producto nuevo.
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
