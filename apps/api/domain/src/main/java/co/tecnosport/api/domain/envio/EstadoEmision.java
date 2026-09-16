package co.tecnosport.api.domain.envio;

/**
 * En qué va el intento de emitir las guías de un pedido (adr/0033).
 *
 * <p>Existe porque <strong>crear un envío en Skydropx no devuelve una guía</strong>: responde
 * {@code 202} con {@code workflow_status: in_progress} y la guía aparece minutos después, o no
 * aparece nunca. Medido: 25 segundos el caso bueno, más de cuatro minutos el que terminó en {@code
 * error} (docs/13-skydropx-capacidades.md §6.10). Entre esos dos instantes hay plata comprometida y
 * nada que mostrar, y eso es justo lo que este estado nombra.
 */
public enum EstadoEmision {

  /** Se pidió y la plataforma cobró; todavía no se sabe si habrá guía. */
  EN_CURSO,

  /** Hay guías vivas con su número. Es el único estado del que sale un despacho. */
  EMITIDA,

  /**
   * Ningún envío llegó a {@code success}. La plataforma reembolsa sola —comprobado cuatro veces—,
   * así que un fallo cuesta tiempo y no plata, y el pedido se puede volver a intentar.
   */
  FALLIDA,

  /**
   * Unos envíos vivieron y otros no, que solo puede pasar en multienvío. <strong>No se resuelve
   * solo</strong>: hay guías pagadas y vivas para parte del pedido, y despachar media compra o
   * reintentarla entera son decisiones con plata de por medio que no toma un programa. Pide ojo
   * humano y por eso no es {@link #FALLIDA}: llamarlo fallo escondería que hay guías que alguien
   * tiene que cancelar o usar.
   */
  PARCIAL;

  /** ¿Sigue esperando respuesta de la plataforma? */
  public boolean enCurso() {
    return this == EN_CURSO;
  }

  /** ¿Terminó, para bien o para mal? De estos tres la plataforma no dirá nada nuevo. */
  public boolean resuelta() {
    return this != EN_CURSO;
  }
}
