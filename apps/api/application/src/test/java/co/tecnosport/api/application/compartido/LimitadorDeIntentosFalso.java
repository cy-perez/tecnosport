package co.tecnosport.api.application.compartido;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Permite siempre salvo que la prueba lo configure para negar — doble de prueba escrito a mano. */
public final class LimitadorDeIntentosFalso implements LimitadorDeIntentos {

  private final List<String> olvidadas = new ArrayList<>();
  private boolean permitirSiempre = true;

  public void denegarSiempre() {
    this.permitirSiempre = false;
  }

  /** Las llaves cuyo conteo mandó olvidar el caso de uso, en orden. */
  public List<String> olvidadas() {
    return List.copyOf(olvidadas);
  }

  @Override
  public boolean permitir(String clave, int maximoIntentos, Duration ventana, Instant ahora) {
    return permitirSiempre;
  }

  @Override
  public void olvidar(String clave) {
    olvidadas.add(clave);
  }
}
