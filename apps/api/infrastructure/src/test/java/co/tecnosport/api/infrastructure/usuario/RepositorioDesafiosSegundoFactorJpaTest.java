package co.tecnosport.api.infrastructure.usuario;

import static org.assertj.core.api.Assertions.assertThat;

import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.usuario.DesafioSegundoFactor;
import co.tecnosport.api.domain.usuario.HuellaDeToken;
import co.tecnosport.api.domain.usuario.Rol;
import co.tecnosport.api.domain.usuario.Usuario;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@Testcontainers
@Transactional
class RepositorioDesafiosSegundoFactorJpaTest {

  @Container @ServiceConnection
  static PostgreSQLContainer postgres =
      new PostgreSQLContainer(DockerImageName.parse("postgres:16"));

  @Autowired private RepositorioDesafiosSegundoFactorJpa repositorio;
  @Autowired private RepositorioUsuariosJpa repositorioUsuarios;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private jakarta.persistence.EntityManager entityManager;

  private UUID crearUsuario() {
    Usuario usuario =
        Usuario.crear(
            new CorreoElectronico("admin-" + UUID.randomUUID() + "@tecnosport.co"),
            "hash",
            Rol.ADMIN,
            Instant.now());
    repositorioUsuarios.guardar(usuario);
    return usuario.id();
  }

  @Test
  void seGuardaPorLaHuellaYSeConsumeUnaVez() {
    UUID usuarioId = crearUsuario();
    Instant ahora = Instant.now();
    DesafioSegundoFactor desafio =
        DesafioSegundoFactor.crear(usuarioId, ahora, Duration.ofMinutes(5));

    repositorio.guardar(desafio);
    entityManager.flush();

    List<String> huellas =
        jdbc.queryForList(
            "select huella from desafio_segundo_factor where usuario_id = ?",
            String.class,
            usuarioId);
    assertThat(huellas).containsExactly(HuellaDeToken.de(desafio.id()));

    DesafioSegundoFactor leido = repositorio.buscarParaConsumir(desafio.id()).orElseThrow();
    assertThat(leido.usuarioId()).isEqualTo(usuarioId);
    leido.marcarUsado(ahora.plusSeconds(1));
    repositorio.guardar(leido);

    assertThat(repositorio.buscarParaConsumir(desafio.id()).orElseThrow().estaVigente(ahora))
        .isFalse();
  }

  @Test
  void unoQueNoExisteNoSeEncuentra() {
    assertThat(repositorio.buscarParaConsumir(UUID.randomUUID())).isEmpty();
  }
}
