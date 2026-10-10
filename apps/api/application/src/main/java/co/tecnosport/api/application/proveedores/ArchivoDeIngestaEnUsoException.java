package co.tecnosport.api.application.proveedores;

/**
 * Un lote que lee el archivo sigue abierto —en la cola, procesando, en pausa o deteniéndose—: sin
 * el zip, el trabajador lo cerraría en error al llegar a él. Con dos chats en un zip, el segundo
 * lote todavía no lo ha leído cuando el primero termina.
 */
public final class ArchivoDeIngestaEnUsoException extends RuntimeException {

  public ArchivoDeIngestaEnUsoException() {
    super(
        "Una ingesta que lee este archivo todavía está en curso o en pausa. Espera a que termine,"
            + " o detenla, para borrar el archivo.");
  }
}
