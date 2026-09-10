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
 * el de respuesta a una petición, una queja o un reclamo cuenta igual.
 *
 * <p><b>{@code INDETERMINADO} ya no se puede producir en producción</b>, y conviene saberlo antes
 * de ir a buscar por qué no aparece: desde {@code ADR-0024} el {@code bean} de {@code
 * CalendarioHabil} calcula los festivos de cualquier año, así que {@code cubre} siempre responde
 * que sí. El valor se queda por dos razones y ninguna es la inercia: hay filas radicadas antes de
 * ese cambio que lo llevan escrito y el panel tiene que saber pintarlas, y el dominio no puede
 * asumir que todo el que le pase un calendario conozca los festivos — de hecho las pruebas le pasan
 * uno que no.
 */
public enum VerdictoPlazo {
  /** Seguro que llegó a tiempo. */
  EN_PLAZO,
  /** Seguro que llegó tarde: el calendario de festivos de ese año estaba cargado. */
  VENCIDO,
  /** Pasó el límite más temprano posible, pero sin festivos cargados no se puede afirmar. */
  INDETERMINADO
}
