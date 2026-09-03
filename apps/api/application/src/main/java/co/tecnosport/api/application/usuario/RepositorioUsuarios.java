package co.tecnosport.api.application.usuario;

import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.usuario.Usuario;
import java.util.Optional;
import java.util.UUID;

public interface RepositorioUsuarios {

  Optional<Usuario> buscarPorCorreo(CorreoElectronico correo);

  Optional<Usuario> buscarPorId(UUID id);

  void guardar(Usuario usuario);
}
