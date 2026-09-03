package co.tecnosport.api.infrastructure.usuario;

import static org.assertj.core.api.Assertions.assertThat;

import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.usuario.Rol;
import co.tecnosport.api.domain.usuario.SesionRefresco;
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
class RepositorioSesionesJpaTest {

  @Container @ServiceConnection
  static PostgreSQLContainer postgres =
      new PostgreSQLContainer(DockerImageName.parse("postgres:16"));

  @Autowired private RepositorioSesionesJpa repositorio;
  @Autowired private RepositorioUsuariosJpa repositorioUsuarios;

  private static final Duration VIGENCIA = Duration.ofDays(30);

  private UUID crearYGuardarUsuario() {
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
  void guardarYBuscarSesionPorId() {
    UUID usuarioId = crearYGuardarUsuario();
    SesionRefresco sesion =
        SesionRefresco.crear(usuarioId, UUID.randomUUID(), Instant.now(), VIGENCIA);

    repositorio.guardar(sesion);

    SesionRefresco encontrada = repositorio.buscarPorId(sesion.id()).orElseThrow();
    assertThat(encontrada.usuarioId()).isEqualTo(usuarioId);
    assertThat(encontrada.familiaId()).isEqualTo(sesion.familiaId());
    assertThat(encontrada.revocadoEn()).isEmpty();
  }

  @Test
  void guardarUnaSesionMarcadaUsadaPersisteElCambio() {
    UUID usuarioId = crearYGuardarUsuario();
    Instant ahora = Instant.now();
    SesionRefresco sesion = SesionRefresco.crear(usuarioId, UUID.randomUUID(), ahora, VIGENCIA);
    sesion.marcarUsado(ahora.plusSeconds(1));

    repositorio.guardar(sesion);

    SesionRefresco encontrada = repositorio.buscarPorId(sesion.id()).orElseThrow();
    assertThat(encontrada.usadoEn()).isPresent();
  }

  @Test
  void revocarFamiliaRevocaSoloLasDeEsaFamilia() {
    UUID usuarioId = crearYGuardarUsuario();
    UUID familiaA = UUID.randomUUID();
    UUID familiaB = UUID.randomUUID();
    Instant ahora = Instant.now();
    SesionRefresco primeraDeA = SesionRefresco.crear(usuarioId, familiaA, ahora, VIGENCIA);
    SesionRefresco segundaDeA =
        SesionRefresco.crear(usuarioId, familiaA, ahora.plusSeconds(1), VIGENCIA);
    SesionRefresco deB = SesionRefresco.crear(usuarioId, familiaB, ahora, VIGENCIA);
    repositorio.guardar(primeraDeA);
    repositorio.guardar(segundaDeA);
    repositorio.guardar(deB);

    repositorio.revocarFamilia(familiaA, ahora.plusSeconds(2));

    assertThat(repositorio.buscarPorId(primeraDeA.id()).orElseThrow().revocadoEn()).isPresent();
    assertThat(repositorio.buscarPorId(segundaDeA.id()).orElseThrow().revocadoEn()).isPresent();
    assertThat(repositorio.buscarPorId(deB.id()).orElseThrow().revocadoEn()).isEmpty();
  }

  @Test
  void revocarFamiliaNoCambiaUnaSesionYaRevocada() {
    UUID usuarioId = crearYGuardarUsuario();
    UUID familiaId = UUID.randomUUID();
    Instant ahora = Instant.now();
    SesionRefresco sesion = SesionRefresco.crear(usuarioId, familiaId, ahora, VIGENCIA);
    repositorio.guardar(sesion);
    repositorio.revocarFamilia(familiaId, ahora.plusSeconds(1));
    Instant primeraRevocacion =
        repositorio.buscarPorId(sesion.id()).orElseThrow().revocadoEn().orElseThrow();

    repositorio.revocarFamilia(familiaId, ahora.plusSeconds(99));

    Instant segundaLectura =
        repositorio.buscarPorId(sesion.id()).orElseThrow().revocadoEn().orElseThrow();
    assertThat(segundaLectura).isEqualTo(primeraRevocacion);
  }
}
