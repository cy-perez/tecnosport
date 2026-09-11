package co.tecnosport.api.application.reintegro;

import java.util.UUID;

/**
 * Cuando el dinero tiene que salir, el monto y el medio no son opcionales: sin ellos no hay
 * constancia, y sin constancia el negocio no puede demostrar que devolvió — que es lo único que la
 * ley le pide demostrar. Es la clase de error que un panel deja pasar en silencio si nadie lo
 * bloquea.
 *
 * <p>Vive en {@code reintegro} y no en {@code pedido}, donde nació, por lo mismo que {@link
 * MontoDeReintegroInvalidoException} y {@link TopeDeReintegro}: la exigen los caminos que devuelven
 * dinero y no uno solo de ellos. La cancelación fue el primero en tenerla y durante un tiempo el
 * único: garantía y reversión llegaban con el monto en nulo hasta el constructor de {@code Dinero}
 * y respondían 500 con un mensaje que no le servía a nadie.
 *
 * <p>Dos fábricas porque son dos preguntas distintas, y quien lee el error necesita la suya: en la
 * cancelación lo que obliga es que el dinero ya había entrado; en la garantía y la reversión, que
 * el desenlace elegido es el que devuelve.
 */
public class ReintegroRequeridoException extends RuntimeException {

  private ReintegroRequeridoException(String mensaje) {
    super(mensaje);
  }

  /** Cancelar un pedido cuyo dinero ya entró exige devolverlo, y devolverlo exige constancia. */
  public static ReintegroRequeridoException porqueElDineroYaEntro(UUID pedidoId) {
    return new ReintegroRequeridoException(
        "El pedido "
            + pedidoId
            + " ya tenía el dinero recibido: cancelarlo exige el monto y el medio del reintegro.");
  }

  /**
   * Registrar un reintegro es, por definición, devolver dinero: sin monto ni medio no hay
   * constancia.
   */
  public static ReintegroRequeridoException porqueUnReintegroSiempreDevuelve() {
    return new ReintegroRequeridoException(
        "Registrar un reintegro exige el monto y el medio: sin ellos no queda constancia de cuánto"
            + " salió ni por dónde.");
  }

  /**
   * Una reversión que hizo el emisor no deja constancia —el dinero no salió de aquí— pero sí tiene
   * que decir cuánto volvió: sin eso, ese pedido podría devolver su total otra vez por otro camino.
   */
  public static ReintegroRequeridoException porqueRevirtioElEmisor() {
    return new ReintegroRequeridoException(
        "Una reversión que hizo el emisor exige cuánto revirtió: sin ese dato, este pedido podría"
            + " devolver su total otra vez por otro camino.");
  }

  /**
   * De las tres salidas de la garantía y de los desenlaces de la reversión, solo algunos devuelven
   * dinero. Elegir uno de ésos y no decir cuánto ni por dónde es dejar la constancia a medias.
   */
  public static ReintegroRequeridoException porqueElDesenlaceDevuelveDinero(String desenlace) {
    return new ReintegroRequeridoException(
        "El desenlace "
            + desenlace
            + " devuelve el dinero: exige el monto y el medio del reintegro.");
  }
}
