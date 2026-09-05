package co.tecnosport.api.presentation.usuario;

import co.tecnosport.api.application.compartido.LimitadorDeIntentos;
import java.time.Duration;
import java.time.Instant;

final class LimitadorDeIntentosDobleDePrueba implements LimitadorDeIntentos {

  private boolean permitirSiempre = true;

  void denegarSiempre() {
    this.permitirSiempre = false;
  }

  /**
   * Bean compartido por todo el contexto de {@code @WebMvcTest}: sin esto, un {@code
   * denegarSiempre()} de una prueba contaminaría a todas las que corran después en la misma clase.
   */
  void reiniciar() {
    this.permitirSiempre = true;
  }

  @Override
  public boolean permitir(String clave, int maximoIntentos, Duration ventana, Instant ahora) {
    return permitirSiempre;
  }
}
