package co.tecnosport.api.application.compartido;

import java.util.function.Supplier;

/**
 * Doble de prueba escrito a mano, sin Mockito, ver docs/06-testing.md.
 *
 * <p>Ejecuta y ya: en una prueba de aplicación no hay transacciones que separar. Lo que sí cuenta
 * es <strong>cuántas veces</strong> se abrió una, porque el orden de las escrituras alrededor del
 * cobro es el diseño y una prueba tiene que poder afirmarlo.
 */
public final class EnTransaccionPropiaFalsa implements EnTransaccionPropia {

  private int veces;

  @Override
  public <T> T ejecutar(Supplier<T> trabajo) {
    veces++;
    return trabajo.get();
  }

  public int veces() {
    return veces;
  }
}
