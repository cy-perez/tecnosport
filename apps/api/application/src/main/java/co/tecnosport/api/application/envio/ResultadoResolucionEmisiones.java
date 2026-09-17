package co.tecnosport.api.application.envio;

import java.util.List;

/**
 * Lo que hizo una corrida de la resolución de emisiones, para el registro de la tarea programada.
 *
 * <p>Son nueve números y ninguno sobra, porque cada uno describe una situación distinta y varias se
 * ven idénticas desde fuera si se suman:
 *
 * <ul>
 *   <li>{@code sinRespuesta} y {@code siguenEnCurso} dejan la emisión igual de abierta, pero uno es
 *       la plataforma sin contestar y el otro una transportadora tardando. Sumarlos haría que un
 *       proveedor caído durante horas se leyera como paciencia.
 *   <li>{@code parciales} debería ser siempre cero. Cuando no lo sea, hay guías pagadas y vivas que
 *       nadie va a usar hasta que una persona las mire.
 *   <li>{@code conError} son las que no se dejaron resolver. Antes no existía: una sola tumbaba la
 *       corrida entera y el único síntoma era una traza.
 *   <li>{@code abandonadas} son solicitudes que nunca registraron respuesta y pasaron a
 *       indeterminadas. Cada una puede ser una guía pagada de la que no tenemos identificador.
 * </ul>
 */
public record ResultadoResolucionEmisiones(
    int revisadas,
    int despachadas,
    int fallidas,
    int parciales,
    int siguenEnCurso,
    int sinRespuesta,
    int conError,
    int abandonadas,
    List<String> errores) {

  public ResultadoResolucionEmisiones {
    errores = List.copyOf(errores == null ? List.of() : errores);
  }

  /** ¿Hay algo que una persona tenga que mirar hoy? */
  public boolean exigeOjoHumano() {
    return parciales > 0 || conError > 0 || abandonadas > 0;
  }
}
