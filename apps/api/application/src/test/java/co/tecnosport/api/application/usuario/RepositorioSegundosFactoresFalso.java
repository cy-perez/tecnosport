package co.tecnosport.api.application.usuario;

import co.tecnosport.api.domain.usuario.SegundoFactor;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Doble de prueba escrito a mano, sin Mockito, ver docs/06-testing.md.
 *
 * <p>Guarda una <b>copia</b> del agregado y entrega otra al buscar, como haría la base: si el caso
 * de uso cambia el agregado y se olvida de guardarlo, la prueba lo ve.
 */
final class RepositorioSegundosFactoresFalso implements RepositorioSegundosFactores {

  private final Map<UUID, SegundoFactor> factores = new HashMap<>();
  private int guardados;

  void con(SegundoFactor factor) {
    factores.put(factor.usuarioId(), copia(factor));
  }

  Optional<SegundoFactor> de(UUID usuarioId) {
    return Optional.ofNullable(factores.get(usuarioId))
        .map(RepositorioSegundosFactoresFalso::copia);
  }

  int guardados() {
    return guardados;
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
    guardados++;
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
