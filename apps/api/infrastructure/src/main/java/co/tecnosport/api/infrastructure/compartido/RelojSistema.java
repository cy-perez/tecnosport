package co.tecnosport.api.infrastructure.compartido;

import co.tecnosport.api.application.compartido.Reloj;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class RelojSistema implements Reloj {

  @Override
  public Instant ahora() {
    return Instant.now();
  }
}
