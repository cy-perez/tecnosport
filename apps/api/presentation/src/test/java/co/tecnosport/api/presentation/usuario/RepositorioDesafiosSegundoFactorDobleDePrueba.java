package co.tecnosport.api.presentation.usuario;

import co.tecnosport.api.application.usuario.RepositorioDesafiosSegundoFactor;
import co.tecnosport.api.domain.usuario.DesafioSegundoFactor;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Guarda copias, como la base. */
final class RepositorioDesafiosSegundoFactorDobleDePrueba
    implements RepositorioDesafiosSegundoFactor {

  private final Map<UUID, DesafioSegundoFactor> desafios = new HashMap<>();

  @Override
  public Optional<DesafioSegundoFactor> buscarParaConsumir(UUID id) {
    return Optional.ofNullable(desafios.get(id))
        .map(RepositorioDesafiosSegundoFactorDobleDePrueba::copia);
  }

  @Override
  public void guardar(DesafioSegundoFactor desafio) {
    desafios.put(desafio.id(), copia(desafio));
  }

  void limpiar() {
    desafios.clear();
  }

  List<DesafioSegundoFactor> todos() {
    return desafios.values().stream()
        .map(RepositorioDesafiosSegundoFactorDobleDePrueba::copia)
        .toList();
  }

  private static DesafioSegundoFactor copia(DesafioSegundoFactor d) {
    return new DesafioSegundoFactor(
        d.id(), d.usuarioId(), d.creadoEn(), d.expiraEn(), d.usadoEn().orElse(null));
  }
}
