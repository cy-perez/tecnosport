package co.tecnosport.api.domain.retracto;

/**
 * Si una solicitud de retracto llegó dentro de los cinco días hábiles del artículo 47 de la Ley
 * 1480 de 2011.
 *
 * <p>Son tres y no dos a propósito. Sin el calendario de festivos cargado, el límite que se puede
 * calcular es el más temprano posible: los festivos solo lo empujan hacia adelante. Entonces "llegó
 * antes de ese límite" sí es una respuesta segura, pero "llegó después" no lo es, y responder
 * {@code VENCIDO} ahí sería negarle un derecho a alguien que quizá está en plazo.
 */
public enum VerdictoPlazo {
  /** Seguro que llegó a tiempo. */
  EN_PLAZO,
  /** Seguro que llegó tarde: el calendario de festivos de ese año estaba cargado. */
  VENCIDO,
  /** Pasó el límite más temprano posible, pero sin festivos cargados no se puede afirmar. */
  INDETERMINADO
}
