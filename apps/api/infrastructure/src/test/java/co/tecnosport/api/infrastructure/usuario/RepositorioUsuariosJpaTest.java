package co.tecnosport.api.infrastructure.usuario;

import static org.assertj.core.api.Assertions.assertThat;

import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.usuario.Rol;
import co.tecnosport.api.domain.usuario.Usuario;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@Testcontainers
@Transactional
class RepositorioUsuariosJpaTest {

  @Container @ServiceConnection
  static PostgreSQLContainer postgres =
      new PostgreSQLContainer(DockerImageName.parse("postgres:16"));

  @Autowired private RepositorioUsuariosJpa repositorio;

  @Test
  void guardarYBuscarUsuarioPorCorreo() {
    CorreoElectronico correo = new CorreoElectronico("admin@tecnosport.co");
    Usuario usuario = Usuario.crear(correo, "hash-bcrypt", Rol.ADMIN, Instant.now());

    repositorio.guardar(usuario);

    Usuario encontrado = repositorio.buscarPorCorreo(correo).orElseThrow();
    assertThat(encontrado.id()).isEqualTo(usuario.id());
    assertThat(encontrado.claveHash()).isEqualTo("hash-bcrypt");
    assertThat(encontrado.rol()).isEqualTo(Rol.ADMIN);
  }

  @Test
  void buscarPorIdFunciona() {
    Usuario usuario =
        Usuario.crear(
            new CorreoElectronico("admin2@tecnosport.co"), "hash-bcrypt", Rol.ADMIN, Instant.now());
    repositorio.guardar(usuario);

    Usuario encontrado = repositorio.buscarPorId(usuario.id()).orElseThrow();

    assertThat(encontrado.correo()).isEqualTo(usuario.correo());
  }

  @Test
  void unCorreoInexistenteNoSeEncuentra() {
    Optional<Usuario> encontrado =
        repositorio.buscarPorCorreo(new CorreoElectronico("no-existe@tecnosport.co"));

    assertThat(encontrado).isEmpty();
  }

  @Test
  void unIdInexistenteNoSeEncuentra() {
    Optional<Usuario> encontrado = repositorio.buscarPorId(UUID.randomUUID());

    assertThat(encontrado).isEmpty();
  }

  @Test
  void correoVerificadoEnSePersisteYSeLee() {
    Usuario usuario =
        Usuario.crear(
            new CorreoElectronico("cliente@tecnosport.co"),
            "hash-bcrypt",
            Rol.CLIENTE,
            Instant.now());
    repositorio.guardar(usuario);

    Usuario reciénCreado = repositorio.buscarPorId(usuario.id()).orElseThrow();
    assertThat(reciénCreado.correoVerificado()).isFalse();

    Instant ahora = Instant.now();
    usuario.verificarCorreo(ahora);
    repositorio.guardar(usuario);

    Usuario verificado = repositorio.buscarPorId(usuario.id()).orElseThrow();
    assertThat(verificado.correoVerificado()).isTrue();
  }
}
