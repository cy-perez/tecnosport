package co.tecnosport.api.application.pago;

import co.tecnosport.api.application.compartido.EnTransaccionPropia;
import java.util.function.Supplier;

/**
 * Corre el trabajo y ya. Es fiel a lo que hace la implementación real para lo que estas pruebas
 * comprueban —que cada trozo se confirma por su cuenta— y no intenta simular una base de datos.
 *
 * <p>Lo que <b>no</b> puede probar un doble en memoria es un rollback, y ahí estaba el fallo
 * original: el caso de uso guardaba el pago y después lanzaba, y era el {@code TransactionTemplate}
 * del controlador el que se llevaba la fila por delante. Por eso la prueba que de verdad protege
 * esto no mira si el pago "está guardado" —eso pasaba también con el código roto— sino <b>la
 * consecuencia observable</b>: que el siguiente intento numere +1 y no repita la factura.
 */
final class EnTransaccionPropiaFalsa implements EnTransaccionPropia {

  int trabajosConfirmados;

  @Override
  public <T> T ejecutar(Supplier<T> trabajo) {
    T resultado = trabajo.get();
    trabajosConfirmados++;
    return resultado;
  }
}
