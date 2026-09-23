package co.tecnosport.api.presentation.pedido;

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
    // CrearPedido no llama a olvidar a proposito: ahi un "acierto" no prueba ningun secreto.
    return permitirSiempre;
  }

  @Override
  public void olvidar(String clave) {
    throw new UnsupportedOperationException("CrearPedido no deberia llamar a olvidar.");
  }
}
