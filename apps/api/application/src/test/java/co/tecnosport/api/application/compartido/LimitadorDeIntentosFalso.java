package co.tecnosport.api.application.compartido;

import java.time.Duration;
import java.time.Instant;

/** Permite siempre salvo que la prueba lo configure para negar — doble de prueba escrito a mano. */
public final class LimitadorDeIntentosFalso implements LimitadorDeIntentos {

  private boolean permitirSiempre = true;

  public void denegarSiempre() {
    this.permitirSiempre = false;
  }

  @Override
  public boolean permitir(String clave, int maximoIntentos, Duration ventana, Instant ahora) {
    return permitirSiempre;
  }
}
