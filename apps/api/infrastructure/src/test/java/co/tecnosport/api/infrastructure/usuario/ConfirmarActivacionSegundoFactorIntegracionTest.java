package co.tecnosport.api.infrastructure.usuario;

import static org.assertj.core.api.Assertions.assertThat;

import co.tecnosport.api.application.compartido.LimitadorDeIntentos;
import co.tecnosport.api.application.compartido.TextoDeCorreo;
import co.tecnosport.api.application.compartido.TextosDeCorreo;
import co.tecnosport.api.application.usuario.ActivacionConfirmada;
import co.tecnosport.api.application.usuario.ConfirmarActivacionSegundoFactor;
import co.tecnosport.api.application.usuario.ConfirmarActivacionSegundoFactorComando;
import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.usuario.Rol;
import co.tecnosport.api.domain.usuario.SegundoFactor;
import co.tecnosport.api.domain.usuario.SesionRefresco;
import co.tecnosport.api.domain.usuario.Totp;
import co.tecnosport.api.domain.usuario.Usuario;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
 * La confirmación guarda el factor activo y sus diez códigos y <b>después</b> revoca todas las
 * sesiones con un {@code update} masivo que limpia el contexto de persistencia. Es la forma exacta
 * del defecto que {@code ConfirmarRecuperacionIntegracionTest} encontró —escrituras descartadas en
 * silencio por un {@code clear} sin {@code flush}—, y solo se ve contra los repositorios JPA de
 * verdad: los dobles de aplicación no tienen contexto que limpiar.
 */
@SpringBootTest
@Testcontainers
@Transactional
class ConfirmarActivacionSegundoFactorIntegracionTest {

  @Container @ServiceConnection
  static PostgreSQLContainer postgres =
      new PostgreSQLContainer(DockerImageName.parse("postgres:16"));

  @Autowired private RepositorioUsuariosJpa repositorioUsuarios;
  @Autowired private RepositorioSesionesJpa repositorioSesiones;
  @Autowired private RepositorioSegundosFactoresJpa repositorioSegundosFactores;
  @Autowired private EntityManager entityManager;

  private static final LimitadorDeIntentos SIN_LIMITE =
      new LimitadorDeIntentos() {
        @Override
        public boolean permitir(String clave, int maximo, Duration ventana, Instant ahora) {
          return true;
        }

        @Override
        public void olvidar(String clave) {}
      };

  private static final TextosDeCorreo TEXTOS =
      new TextosDeCorreo() {
        @Override
        public String texto(TextoDeCorreo texto, Object... argumentos) {
          return texto.clave();
        }

        @Override
        public String dinero(Dinero valor) {
          return valor.toString();
        }
      };

  @Test
  void lasEscriturasDelFactorSobrevivenARevocarLasSesiones() {
    Instant ahora = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    Usuario usuario =
        Usuario.crear(
            new CorreoElectronico("integracion-" + UUID.randomUUID() + "@tecnosport.co"),
            "hash:clave",
            Rol.ADMIN,
            ahora);
    usuario.verificarCorreo(ahora);
    repositorioUsuarios.guardar(usuario);
    SesionRefresco vieja =
        SesionRefresco.crear(usuario.id(), UUID.randomUUID(), ahora, Duration.ofDays(30));
    repositorioSesiones.guardar(vieja);
    SegundoFactor pendiente = SegundoFactor.iniciar(usuario.id(), ahora);
    repositorioSegundosFactores.guardar(pendiente);
    entityManager.flush();
    entityManager.clear();

    ConfirmarActivacionSegundoFactor caso =
        new ConfirmarActivacionSegundoFactor(
            repositorioUsuarios,
            repositorioSegundosFactores,
            repositorioSesiones,
            (u, alcance, instante) -> "token",
            (destinatario, nombre, asunto, cuerpo) -> {},
            TEXTOS,
            () -> ahora,
            Duration.ofDays(30),
            Duration.ofMinutes(15),
            SIN_LIMITE,
            5,
            Duration.ofMinutes(15));

    ActivacionConfirmada confirmada =
        caso.ejecutar(
            new ConfirmarActivacionSegundoFactorComando(
                usuario.id(), Totp.codigoEn(pendiente.clave(), Totp.pasoDe(ahora))));
    entityManager.flush();
    entityManager.clear();

    SegundoFactor guardado =
        repositorioSegundosFactores.buscarParaActualizar(usuario.id()).orElseThrow();
    assertThat(guardado.activo()).isTrue();
    assertThat(guardado.restantes()).isEqualTo(SegundoFactor.CANTIDAD_DE_CODIGOS);
    assertThat(repositorioSesiones.buscarPorId(vieja.id()).orElseThrow().revocadoEn()).isPresent();
    assertThat(
            repositorioSesiones
                .buscarPorId(confirmada.sesion().refreshTokenId())
                .orElseThrow()
                .estaVigente(ahora))
        .isTrue();
  }
}
