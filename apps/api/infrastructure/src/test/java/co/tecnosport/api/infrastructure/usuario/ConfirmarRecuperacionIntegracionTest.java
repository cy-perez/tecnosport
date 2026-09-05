package co.tecnosport.api.infrastructure.usuario;

import static org.assertj.core.api.Assertions.assertThat;

import co.tecnosport.api.application.usuario.CodificadorDeClaves;
import co.tecnosport.api.application.usuario.ConfirmarRecuperacion;
import co.tecnosport.api.application.usuario.ConfirmarRecuperacionComando;
import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.usuario.Rol;
import co.tecnosport.api.domain.usuario.SesionRefresco;
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

/**
 * Ejercita {@link ConfirmarRecuperacion} contra los tres repositorios JPA reales en la misma
 * transacción — a diferencia de {@code ConfirmarRecuperacionTest} (aplicación, con dobles escritos
 * a mano), esta es la única prueba que puede atrapar un bug de flush/clear de Hibernate entre
 * escrituras: un doble de prueba no reproduce ese comportamiento. Encontrado así, a mano contra
 * {@code bootRun} real: {@code revocarTodasDeUsuario} limpiaba el contexto de persistencia sin
 * volcar antes el token consumido ni la clave nueva, y los descartaba en silencio, sin ninguna
 * excepción (ver el javadoc de {@code SesionRefrescoJpaRepository.revocarTodasDeUsuario}).
 */
@SpringBootTest
@Testcontainers
@Transactional
class ConfirmarRecuperacionIntegracionTest {

  @Container @ServiceConnection
  static PostgreSQLContainer postgres =
      new PostgreSQLContainer(DockerImageName.parse("postgres:16"));

  @Autowired private RepositorioTokensRecuperacionJpa repositorioTokens;
  @Autowired private RepositorioUsuariosJpa repositorioUsuarios;
  @Autowired private RepositorioSesionesJpa repositorioSesiones;

  private static final Duration VIGENCIA_TOKEN = Duration.ofMinutes(30);
  private static final Duration VIGENCIA_REFRESCO = Duration.ofDays(30);

  private static final class CodificadorDeClavesDePrueba implements CodificadorDeClaves {
    @Override
    public String codificar(String claveTextoPlano) {
      return "hash:" + claveTextoPlano;
    }

    @Override
    public boolean verificar(String claveTextoPlano, String claveHash) {
      return codificar(claveTextoPlano).equals(claveHash);
    }
  }

  @Test
  void confirmarPersisteElTokenUsadoLaClaveNuevaYRevocaLasSesiones() {
    Instant ahora = Instant.now();
    Usuario usuario =
        Usuario.crear(
            new CorreoElectronico("integracion-" + UUID.randomUUID() + "@tecnosport.co"),
            "hash:clave-vieja",
            Rol.CLIENTE,
            ahora);
    repositorioUsuarios.guardar(usuario);
    SesionRefresco sesion =
        SesionRefresco.crear(usuario.id(), UUID.randomUUID(), ahora, VIGENCIA_REFRESCO);
    repositorioSesiones.guardar(sesion);
    TokenRecuperacionClave token =
        TokenRecuperacionClave.crear(usuario.id(), ahora, VIGENCIA_TOKEN);
    repositorioTokens.guardar(token);

    ConfirmarRecuperacion caso =
        new ConfirmarRecuperacion(
            repositorioTokens,
            repositorioUsuarios,
            repositorioSesiones,
            new CodificadorDeClavesDePrueba(),
            () -> ahora);

    caso.ejecutar(new ConfirmarRecuperacionComando(token.id().toString(), "clave-nueva"));

    assertThat(repositorioTokens.buscarPorId(token.id()).orElseThrow().usadoEn()).isPresent();
    assertThat(repositorioUsuarios.buscarPorId(usuario.id()).orElseThrow().claveHash())
        .isEqualTo("hash:clave-nueva");
    assertThat(repositorioSesiones.buscarPorId(sesion.id()).orElseThrow().revocadoEn()).isPresent();
  }
}
