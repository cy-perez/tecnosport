package co.tecnosport.api.presentation.pedido;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Las cuentas a las que se puede transferir. Eran cuatro campos sueltos —una sola cuenta— hasta el
 * 28 de septiembre de 2026, y esta clase no tenía prueba propia: con un único registro plano, lo
 * único que había que cuidar era que ninguno viniera vacío, y eso lo decía el constructor.
 *
 * <p>Con una lista aparecen dos cosas que sí pueden romperse en silencio, y son las dos que están
 * aquí.
 */
class PropiedadesTransferenciaManualTest {

  private static CuentaDeTransferencia nequi() {
    return new CuentaDeTransferencia("Nequi", "billetera", "300 000 0000", "Tecno Sport");
  }

  /**
   * <b>Sin ninguna cuenta, el checkout ofrecería "transferencia bancaria" y la pantalla siguiente
   * no tendría a dónde mandar a nadie.</b> Un método de pago que no se puede completar es peor que
   * uno que no se ofrece, así que esto impide arrancar: un despliegue mal configurado tiene que
   * fallar al arrancar y no en el primer pedido, que es el mismo criterio que usa la lista de
   * métodos habilitados de Wompi.
   */
  @Test
  void sinNingunaCuentaNoArranca() {
    IllegalStateException vacia =
        assertThrows(
            IllegalStateException.class, () -> new PropiedadesTransferenciaManual(List.of()));
    assertTrue(vacia.getMessage().contains("cuentas"));

    assertThrows(IllegalStateException.class, () -> new PropiedadesTransferenciaManual(null));
  }

  /**
   * El orden en que se declaran es el orden en que se le ofrecen al comprador, y eso es parte de lo
   * que se publica: quien transfiere elige una, así que cuál va primero no es un detalle de
   * serialización. {@code List.copyOf} conserva el orden — la prueba está para que nadie cambie esa
   * copia por un {@code Set} buscando quitar duplicados.
   */
  @Test
  void conservaElOrdenEnQueSeDeclararon() {
    CuentaDeTransferencia daviplata =
        new CuentaDeTransferencia("Daviplata", "billetera", "310 000 0000", "Tecno Sport");
    CuentaDeTransferencia bbva =
        new CuentaDeTransferencia("BBVA", "ahorros", "123-456789-00", "Tecno Sport");

    PropiedadesTransferenciaManual propiedades =
        new PropiedadesTransferenciaManual(List.of(nequi(), daviplata, bbva));

    assertEquals(List.of(nequi(), daviplata, bbva), propiedades.cuentas());
  }

  /** Un campo vacío en una cuenta es un dato que nadie puede usar para transferir. */
  @Test
  void unaCuentaConUnCampoVacioNoArranca() {
    assertThrows(
        IllegalStateException.class,
        () -> new CuentaDeTransferencia("Nequi", "billetera", "  ", "Tecno Sport"));
    assertThrows(
        IllegalStateException.class,
        () -> new CuentaDeTransferencia(null, "billetera", "300 000 0000", "Tecno Sport"));
  }
}
