package co.tecnosport.api.domain.compartido;

/**
 * Si algo ocurrió dentro de un plazo contado en días hábiles.
 *
 * <p>Son tres valores y no dos a propósito. Sin el calendario de festivos cargado, el límite que se
 * puede calcular es el más temprano posible: los festivos solo lo empujan hacia adelante. Entonces
 * "llegó antes de ese límite" sí es una respuesta segura, pero "llegó después" no lo es, y
 * responder {@code VENCIDO} ahí sería afirmar un incumplimiento que quizá no ocurrió.
 *
 * <p>Nació para el retracto (Ley 1480 de 2011, art. 47, cinco días hábiles desde la entrega) y vive
 * en {@code compartido} porque el mismo razonamiento vale para cualquier plazo hábil del sistema:
 * el de respuesta a una petición, una queja o un reclamo cuenta igual, con el mismo calendario sin
 * cargar detrás.
 */
public enum VerdictoPlazo {
  /** Seguro que llegó a tiempo. */
  EN_PLAZO,
  /** Seguro que llegó tarde: el calendario de festivos de ese año estaba cargado. */
  VENCIDO,
  /** Pasó el límite más temprano posible, pero sin festivos cargados no se puede afirmar. */
  INDETERMINADO
}
