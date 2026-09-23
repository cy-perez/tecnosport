package co.tecnosport.api.presentation.usuario;

import co.tecnosport.api.application.usuario.RepositorioUsuarios;
import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.usuario.Usuario;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

final class RepositorioUsuariosDobleDePrueba implements RepositorioUsuarios {

  private final Map<UUID, Usuario> usuarios = new HashMap<>();

  void conUsuario(Usuario usuario) {
    rechazarSiElCorreoYaEsDeOtro(usuario);
    usuarios.put(usuario.id(), usuario);
  }

  void limpiar() {
    usuarios.clear();
  }

  @Override
  public Optional<Usuario> buscarPorCorreo(CorreoElectronico correo) {
    return usuarios.values().stream().filter(u -> u.correo().equals(correo)).findFirst();
  }

  @Override
  public Optional<Usuario> buscarPorId(UUID id) {
    return Optional.ofNullable(usuarios.get(id));
  }

  @Override
  public void guardar(Usuario usuario) {
    rechazarSiElCorreoYaEsDeOtro(usuario);
    usuarios.put(usuario.id(), usuario);
  }

  /**
   * El correo es único en el repositorio real, y el doble tiene que serlo también. Si acepta dos
   * usuarios con el mismo correo, {@link #buscarPorCorreo} devuelve uno cualquiera de los dos según
   * el orden de iteración del mapa, que depende de los UUID aleatorios de las llaves: una prueba
   * contaminada falla entonces una de cada tantas corridas en vez de fallar siempre.
   */
  private void rechazarSiElCorreoYaEsDeOtro(Usuario usuario) {
    boolean ocupado =
        usuarios.values().stream()
            .anyMatch(
                otro -> otro.correo().equals(usuario.correo()) && !otro.id().equals(usuario.id()));
    if (ocupado) {
      throw new IllegalStateException(
          "Ya hay otro usuario con el correo "
              + usuario.correo().valor()
              + " en el doble de prueba. Si es el montaje de otra prueba, falta limpiarlo entre"
              + " métodos; si la prueba necesita dos usuarios, dales correos distintos.");
    }
  }
}
