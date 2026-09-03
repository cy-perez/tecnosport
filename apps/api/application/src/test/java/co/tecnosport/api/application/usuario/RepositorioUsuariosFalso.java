package co.tecnosport.api.application.usuario;

import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.usuario.Usuario;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Doble de prueba escrito a mano, sin Mockito, ver docs/06-testing.md. */
final class RepositorioUsuariosFalso implements RepositorioUsuarios {

  private final Map<UUID, Usuario> usuarios = new HashMap<>();

  void conUsuario(Usuario usuario) {
    usuarios.put(usuario.id(), usuario);
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
    usuarios.put(usuario.id(), usuario);
  }
}
