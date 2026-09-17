package co.tecnosport.api.presentation.pedido;

import co.tecnosport.api.application.compartido.EnTransaccionPropia;
import java.util.function.Supplier;

/** Doble de prueba escrito a mano, sin Mockito, ver docs/06-testing.md. Ejecuta y ya. */
public class EnTransaccionPropiaDobleDePrueba implements EnTransaccionPropia {

  @Override
  public <T> T ejecutar(Supplier<T> trabajo) {
    return trabajo.get();
  }
}
