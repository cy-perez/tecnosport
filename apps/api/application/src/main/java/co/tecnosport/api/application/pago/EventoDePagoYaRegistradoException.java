package co.tecnosport.api.application.pago;

/**
 * El evento que esta notificación traía ya estaba guardado para este pago: otra copia de la misma
 * notificación ganó la carrera y lo aplicó.
 *
 * <p><b>No es un error del que haya que avisarle a nadie.</b> El estado quedó bien —lo dejó la
 * gemela— y lo único que hay que evitar es contestarle a la pasarela algo que la haga reintentar.
 * Por eso existe: quien la reciba la traduce a "ya procesado" y responde 200.
 *
 * <p>La guarda de {@code ProcesarNotificacionSistecredito} —un pago que ya no está {@code
 * PENDIENTE} no admite más transiciones— cubre las repeticiones <b>en serie</b>, que son casi
 * todas. No cubre las simultáneas: dos notificaciones que entran a la vez leen las dos el pago
 * pendiente, las dos lo aplican, y la segunda choca contra el índice único de {@code evento_pago}
 * al volcar. Eso pasó de verdad: el 23 de septiembre de 2026, en las dos corridas de prueba contra
 * el despliegue de dev, Sistecrédito mandó la notificación por duplicado y la segunda salió por
 * "Error inesperado sin manejar" con un 500 para la pasarela.
 *
 * <p>Lo que protege los datos es el índice, y eso no cambia. Esto solo le pone nombre al choque.
 */
public class EventoDePagoYaRegistradoException extends RuntimeException {

  private final String referencia;
  private final String idEvento;

  public EventoDePagoYaRegistradoException(String referencia, String idEvento) {
    super(
        "El evento "
            + idEvento
            + " ya estaba registrado para el pago "
            + referencia
            + ": otra notificación igual lo aplicó primero.");
    this.referencia = referencia;
    this.idEvento = idEvento;
  }

  public String referencia() {
    return referencia;
  }

  public String idEvento() {
    return idEvento;
  }
}
