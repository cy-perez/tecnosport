package co.tecnosport.api.application.usuario;

import co.tecnosport.api.domain.usuario.DesafioSegundoFactor;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Doble de prueba escrito a mano, sin Mockito, ver docs/06-testing.md. Guarda copias. */
final class RepositorioDesafiosSegundoFactorFalso implements RepositorioDesafiosSegundoFactor {

  private final Map<UUID, DesafioSegundoFactor> desafios = new HashMap<>();

  @Override
  public Optional<DesafioSegundoFactor> buscarParaConsumir(UUID id) {
    return Optional.ofNullable(desafios.get(id)).map(RepositorioDesafiosSegundoFactorFalso::copia);
  }

  @Override
  public void guardar(DesafioSegundoFactor desafio) {
    desafios.put(desafio.id(), copia(desafio));
  }

  List<DesafioSegundoFactor> todos() {
    return desafios.values().stream().map(RepositorioDesafiosSegundoFactorFalso::copia).toList();
  }

  private static DesafioSegundoFactor copia(DesafioSegundoFactor d) {
    return new DesafioSegundoFactor(
        d.id(), d.usuarioId(), d.creadoEn(), d.expiraEn(), d.usadoEn().orElse(null));
  }
}
