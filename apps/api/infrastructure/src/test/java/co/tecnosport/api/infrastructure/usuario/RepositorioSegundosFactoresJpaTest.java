package co.tecnosport.api.infrastructure.usuario;

import static org.assertj.core.api.Assertions.assertThat;

import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.usuario.CodigoDeRespaldo;
import co.tecnosport.api.domain.usuario.Rol;
import co.tecnosport.api.domain.usuario.SegundoFactor;
import co.tecnosport.api.domain.usuario.Totp;
import co.tecnosport.api.domain.usuario.Usuario;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
class RepositorioSegundosFactoresJpaTest {

  @Container @ServiceConnection
  static PostgreSQLContainer postgres =
      new PostgreSQLContainer(DockerImageName.parse("postgres:16"));

  @Autowired private RepositorioSegundosFactoresJpa repositorio;
  @Autowired private RepositorioUsuariosJpa repositorioUsuarios;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private EntityManager entityManager;

  // Postgres guarda microsegundos; un Instant con nanosegundos no volvería igual.
  private static final Instant AHORA = Instant.now().truncatedTo(ChronoUnit.MILLIS);

  private UUID crearUsuario() {
    Usuario usuario =
        Usuario.crear(
            new CorreoElectronico("admin-" + UUID.randomUUID() + "@tecnosport.co"),
            "hash",
            Rol.ADMIN,
            AHORA);
    repositorioUsuarios.guardar(usuario);
    return usuario.id();
  }

  /** Lo que de verdad está en la base, sin la caché de primer nivel de por medio. */
  private void vaciarContexto() {
    entityManager.flush();
    entityManager.clear();
  }

  @Test
  void guardaUnoPendienteYLoDevuelveConLaMismaClave() {
    UUID usuarioId = crearUsuario();
    SegundoFactor pendiente = SegundoFactor.iniciar(usuarioId, AHORA);

    repositorio.guardar(pendiente);
    vaciarContexto();

    SegundoFactor leido = repositorio.buscarParaActualizar(usuarioId).orElseThrow();
    assertThat(leido.clave()).isEqualTo(pendiente.clave());
    assertThat(leido.activo()).isFalse();
    assertThat(repositorio.estaActivo(usuarioId)).isFalse();
  }

  @Test
  void laColumnaNoContieneLaClaveNiLosCodigos() {
    UUID usuarioId = crearUsuario();
    SegundoFactor factor = SegundoFactor.iniciar(usuarioId, AHORA);
    List<CodigoDeRespaldo> codigos =
        factor.confirmar(
            Totp.codigoEn(factor.clave(), Totp.pasoDe(AHORA)), AHORA, Duration.ofMinutes(15));

    repositorio.guardar(factor);
    vaciarContexto();

    String columna =
        jdbc.queryForObject(
            "select secreto_cifrado from segundo_factor where usuario_id = ?",
            String.class,
            usuarioId);
    assertThat(columna).startsWith("v1:").doesNotContain(factor.clave().enBase32());
    List<String> huellas =
        jdbc.queryForList(
            "select huella from codigo_de_respaldo where usuario_id = ?", String.class, usuarioId);
    assertThat(huellas).hasSize(SegundoFactor.CANTIDAD_DE_CODIGOS);
    for (CodigoDeRespaldo codigo : codigos) {
      assertThat(huellas).contains(codigo.huella());
    }
  }

  @Test
  void elUsoDeUnCodigoYElUltimoPasoSobreviven() {
    UUID usuarioId = crearUsuario();
    SegundoFactor factor = SegundoFactor.iniciar(usuarioId, AHORA);
    List<CodigoDeRespaldo> codigos =
        factor.confirmar(
            Totp.codigoEn(factor.clave(), Totp.pasoDe(AHORA)), AHORA, Duration.ofMinutes(15));
    repositorio.guardar(factor);
    vaciarContexto();

    SegundoFactor leido = repositorio.buscarParaActualizar(usuarioId).orElseThrow();
    leido.verificar(codigos.get(0).mostrado(), AHORA);
    repositorio.guardar(leido);
    vaciarContexto();

    SegundoFactor otraVez = repositorio.buscarParaActualizar(usuarioId).orElseThrow();
    assertThat(otraVez.restantes()).isEqualTo(SegundoFactor.CANTIDAD_DE_CODIGOS - 1);
    assertThat(otraVez.ultimoPasoUsado()).contains(Totp.pasoDe(AHORA));
    assertThat(otraVez.activadoEn()).isPresent();
    assertThat(repositorio.estaActivo(usuarioId)).isTrue();
  }

  @Test
  void regenerarReemplazaLasFilasDeLosCodigos() {
    UUID usuarioId = crearUsuario();
    SegundoFactor factor = SegundoFactor.iniciar(usuarioId, AHORA);
    List<CodigoDeRespaldo> viejos =
        factor.confirmar(
            Totp.codigoEn(factor.clave(), Totp.pasoDe(AHORA)), AHORA, Duration.ofMinutes(15));
    repositorio.guardar(factor);
    vaciarContexto();

    SegundoFactor leido = repositorio.buscarParaActualizar(usuarioId).orElseThrow();
    List<CodigoDeRespaldo> nuevos = leido.regenerarCodigos(AHORA);
    repositorio.guardar(leido);
    vaciarContexto();

    List<String> huellas =
        jdbc.queryForList(
            "select huella from codigo_de_respaldo where usuario_id = ?", String.class, usuarioId);
    assertThat(huellas).hasSize(SegundoFactor.CANTIDAD_DE_CODIGOS);
    assertThat(huellas).contains(nuevos.get(0).huella()).doesNotContain(viejos.get(0).huella());
  }

  @Test
  void eliminarBorraElFactorYSusCodigos() {
    UUID usuarioId = crearUsuario();
    SegundoFactor factor = SegundoFactor.iniciar(usuarioId, AHORA);
    factor.confirmar(
        Totp.codigoEn(factor.clave(), Totp.pasoDe(AHORA)), AHORA, Duration.ofMinutes(15));
    repositorio.guardar(factor);
    vaciarContexto();

    repositorio.eliminar(usuarioId);
    vaciarContexto();

    assertThat(repositorio.buscarParaActualizar(usuarioId)).isEmpty();
    assertThat(
            jdbc.queryForObject(
                "select count(*) from codigo_de_respaldo where usuario_id = ?",
                Integer.class,
                usuarioId))
        .isZero();
    // Idempotente.
    repositorio.eliminar(usuarioId);
  }

  @Test
  void borrarElUsuarioBorraEnCascadaSuFactorYSusCodigos() {
    UUID usuarioId = crearUsuario();
    SegundoFactor factor = SegundoFactor.iniciar(usuarioId, AHORA);
    factor.confirmar(
        Totp.codigoEn(factor.clave(), Totp.pasoDe(AHORA)), AHORA, Duration.ofMinutes(15));
    repositorio.guardar(factor);
    vaciarContexto();

    jdbc.update("delete from usuario where id = ?", usuarioId);

    assertThat(
            jdbc.queryForObject(
                "select count(*) from segundo_factor where usuario_id = ?",
                Integer.class,
                usuarioId))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "select count(*) from codigo_de_respaldo where usuario_id = ?",
                Integer.class,
                usuarioId))
        .isZero();
  }

  @Test
  void unUsuarioSinFactorNoLoTiene() {
    UUID usuarioId = crearUsuario();
    assertThat(repositorio.buscarParaActualizar(usuarioId)).isEmpty();
    assertThat(repositorio.estaActivo(usuarioId)).isFalse();
  }
}
