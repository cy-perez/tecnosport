package co.tecnosport.api.presentation.usuario;

import co.tecnosport.api.application.usuario.RepositorioSegundosFactores;
import co.tecnosport.api.domain.usuario.SegundoFactor;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Guarda y entrega copias, como la base: ver el de las pruebas de aplicación. */
final class RepositorioSegundosFactoresDobleDePrueba implements RepositorioSegundosFactores {

  private final Map<UUID, SegundoFactor> factores = new HashMap<>();

  void con(SegundoFactor factor) {
    factores.put(factor.usuarioId(), copia(factor));
  }

  Optional<SegundoFactor> de(UUID usuarioId) {
    return Optional.ofNullable(factores.get(usuarioId))
        .map(RepositorioSegundosFactoresDobleDePrueba::copia);
  }

  void limpiar() {
    factores.clear();
  }

  @Override
  public Optional<SegundoFactor> buscarParaActualizar(UUID usuarioId) {
    return de(usuarioId);
  }

  @Override
  public boolean estaActivo(UUID usuarioId) {
    return de(usuarioId).map(SegundoFactor::activo).orElse(false);
  }

  @Override
  public void guardar(SegundoFactor segundoFactor) {
    factores.put(segundoFactor.usuarioId(), copia(segundoFactor));
  }

  @Override
  public void eliminar(UUID usuarioId) {
    factores.remove(usuarioId);
  }

  private static SegundoFactor copia(SegundoFactor f) {
    return new SegundoFactor(
        f.usuarioId(),
        f.clave(),
        f.creadoEn(),
        f.activadoEn().orElse(null),
        f.ultimoPasoUsado().orElse(null),
        f.respaldos());
  }
}
