package co.tecnosport.api.infrastructure.usuario;

import co.tecnosport.api.application.usuario.RepositorioUsuarios;
import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.usuario.Rol;
import co.tecnosport.api.domain.usuario.Usuario;
import co.tecnosport.api.infrastructure.usuario.entidad.UsuarioJpaEntity;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class RepositorioUsuariosJpa implements RepositorioUsuarios {

  private final UsuarioJpaRepository usuarios;

  public RepositorioUsuariosJpa(UsuarioJpaRepository usuarios) {
    this.usuarios = Objects.requireNonNull(usuarios);
  }

  @Override
  public Optional<Usuario> buscarPorCorreo(CorreoElectronico correo) {
    return usuarios.findByCorreo(correo.valor()).map(this::aUsuario);
  }

  @Override
  public Optional<Usuario> buscarPorId(UUID id) {
    return usuarios.findById(id).map(this::aUsuario);
  }

  @Override
  public void guardar(Usuario usuario) {
    usuarios.save(aEntidad(usuario));
  }

  private Usuario aUsuario(UsuarioJpaEntity entidad) {
    return new Usuario(
        entidad.getId(),
        new CorreoElectronico(entidad.getCorreo()),
        entidad.getClaveHash(),
        Rol.valueOf(entidad.getRol()),
        entidad.getCreadoEn());
  }

  private UsuarioJpaEntity aEntidad(Usuario usuario) {
    return new UsuarioJpaEntity(
        usuario.id(),
        usuario.correo().valor(),
        usuario.claveHash(),
        usuario.rol().name(),
        usuario.creadoEn());
  }
}
