package co.tecnosport.api.presentation.compartido;

import co.tecnosport.api.application.compartido.LimitadorDeIntentos;
import java.time.Duration;
import java.time.Instant;

/** Doble de prueba escrito a mano, sin Mockito, ver docs/06-testing.md. */
final class LimitadorDeIntentosFalso implements LimitadorDeIntentos {

  private boolean permitirSiguiente = true;
  private String ultimaClave;

  void denegarSiguiente() {
    this.permitirSiguiente = false;
  }

  String ultimaClave() {
    return ultimaClave;
  }

  @Override
  public boolean permitir(String clave, int maximoIntentos, Duration ventana, Instant ahora) {
    this.ultimaClave = clave;
    return permitirSiguiente;
  }
}
