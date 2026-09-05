package co.tecnosport.api.infrastructure.usuario;

import static org.assertj.core.api.Assertions.assertThat;

import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.usuario.Rol;
import co.tecnosport.api.domain.usuario.TokenRecuperacionClave;
import co.tecnosport.api.domain.usuario.Usuario;
import java.time.Duration;
import java.time.Instant;
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
class RepositorioTokensRecuperacionJpaTest {

  @Container @ServiceConnection
  static PostgreSQLContainer postgres =
      new PostgreSQLContainer(DockerImageName.parse("postgres:16"));

  @Autowired private RepositorioTokensRecuperacionJpa repositorio;
  @Autowired private RepositorioUsuariosJpa repositorioUsuarios;

  private static final Duration VIGENCIA = Duration.ofMinutes(30);

  private UUID crearYGuardarUsuario() {
    Usuario usuario =
        Usuario.crear(
            new CorreoElectronico("cliente-" + UUID.randomUUID() + "@tecnosport.co"),
            "hash",
            Rol.CLIENTE,
            Instant.now());
    repositorioUsuarios.guardar(usuario);
    return usuario.id();
  }

  @Test
  void guardarYBuscarTokenPorId() {
    UUID usuarioId = crearYGuardarUsuario();
    TokenRecuperacionClave token = TokenRecuperacionClave.crear(usuarioId, Instant.now(), VIGENCIA);

    repositorio.guardar(token);

    TokenRecuperacionClave encontrado = repositorio.buscarPorId(token.id()).orElseThrow();
    assertThat(encontrado.usuarioId()).isEqualTo(usuarioId);
    assertThat(encontrado.usadoEn()).isEmpty();
  }

  @Test
  void guardarUnTokenMarcadoUsadoPersisteElCambio() {
    UUID usuarioId = crearYGuardarUsuario();
    Instant ahora = Instant.now();
    TokenRecuperacionClave token = TokenRecuperacionClave.crear(usuarioId, ahora, VIGENCIA);
    token.marcarUsado(ahora.plusSeconds(1));

    repositorio.guardar(token);

    TokenRecuperacionClave encontrado = repositorio.buscarPorId(token.id()).orElseThrow();
    assertThat(encontrado.usadoEn()).isPresent();
  }

  @Test
  void unIdInexistenteNoSeEncuentra() {
    assertThat(repositorio.buscarPorId(UUID.randomUUID())).isEmpty();
  }
}
