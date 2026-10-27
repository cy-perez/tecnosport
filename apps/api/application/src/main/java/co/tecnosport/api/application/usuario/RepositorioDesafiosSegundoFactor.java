package co.tecnosport.api.application.usuario;

import co.tecnosport.api.domain.usuario.DesafioSegundoFactor;
import java.util.Optional;
import java.util.UUID;

/** Los segundos pasos pendientes de un inicio de sesión (ADR-0096), guardados por su huella. */
public interface RepositorioDesafiosSegundoFactor {

  /**
   * Con bloqueo pesimista: dos verificaciones del mismo desafío a la vez no pueden consumirlo las
   * dos. Exige una transacción abierta por quien llama.
   */
  Optional<DesafioSegundoFactor> buscarParaConsumir(UUID id);

  void guardar(DesafioSegundoFactor desafio);
}
