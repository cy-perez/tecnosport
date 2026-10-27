package co.tecnosport.api.infrastructure.usuario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.usuario.DesafioSegundoFactor;
import co.tecnosport.api.domain.usuario.Rol;
import co.tecnosport.api.domain.usuario.SegundoFactor;
import co.tecnosport.api.domain.usuario.SesionRefresco;
import co.tecnosport.api.domain.usuario.Totp;
import co.tecnosport.api.domain.usuario.Usuario;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * El procedimiento de {@code infra/operacion/restablecer-segundo-factor.sql} (ADR-0096), ejecutado
 * tal cual —con el marcador del correo reemplazado, que es lo único que la persona edita— contra
 * PostgreSQL con las migraciones de verdad. Un archivo de operación que nadie corre hasta el día
 * que hace falta es justo el que falla ese día: una columna renombrada, una tabla nueva que no
 * borra.
 *
 * <p>Sin {@code @Transactional}: el bloque {@code DO} tiene que confirmar, como en la consola.
 */
@SpringBootTest
@Testcontainers
class RestablecerSegundoFactorSqlTest {

  @Container @ServiceConnection
  static PostgreSQLContainer postgres =
      new PostgreSQLContainer(DockerImageName.parse("postgres:16"));

  private static final String MARCADOR = "CORREO_DEL_ADMIN";

  @Autowired private RepositorioUsuariosJpa usuarios;
  @Autowired private RepositorioSegundosFactoresJpa factores;
  @Autowired private RepositorioSesionesJpa sesiones;
  @Autowired private RepositorioDesafiosSegundoFactorJpa desafios;
  @Autowired private JdbcTemplate jdbc;

  private static String procedimiento() throws IOException {
    Path candidato = Path.of("").toAbsolutePath();
    while (candidato != null && !Files.isDirectory(candidato.resolve("infra/operacion"))) {
      candidato = candidato.getParent();
    }
    if (candidato == null) {
      throw new IllegalStateException("No se encontró infra/operacion subiendo desde aquí.");
    }
    return Files.readString(
        candidato.resolve("infra/operacion/restablecer-segundo-factor.sql"),
        StandardCharsets.UTF_8);
  }

  /** Un ADMIN con factor activo, una sesión abierta, un desafío pendiente y conteos del límite. */
  private Usuario adminConTodo() {
    Instant ahora = Instant.now();
    Usuario admin =
        Usuario.crear(
            new CorreoElectronico("admin-" + UUID.randomUUID() + "@tecnosport.co"),
            "hash",
            Rol.ADMIN,
            ahora);
    admin.verificarCorreo(ahora);
    usuarios.guardar(admin);
    SegundoFactor factor = SegundoFactor.iniciar(admin.id(), ahora);
    factor.confirmar(
        Totp.codigoEn(factor.clave(), Totp.pasoDe(ahora)), ahora, Duration.ofMinutes(15));
    factores.guardar(factor);
    sesiones.guardar(
        SesionRefresco.crear(admin.id(), UUID.randomUUID(), ahora, Duration.ofDays(30)));
    desafios.guardar(DesafioSegundoFactor.crear(admin.id(), ahora, Duration.ofMinutes(5)));
    Timestamp vence = Timestamp.from(ahora.plus(Duration.ofMinutes(15)));
    jdbc.update(
        "insert into limite_intentos (clave, contador, ventana_expira_en) values (?, 10, ?)",
        "cuenta:segundo-factor:" + admin.id(),
        vence);
    jdbc.update(
        "insert into limite_intentos (clave, contador, ventana_expira_en) values (?, 5, ?)",
        "cuenta-ip:segundo-factor:" + admin.id() + ":203.0.113.5",
        vence);
    return admin;
  }

  private int contar(String sql, UUID usuarioId) {
    return jdbc.queryForObject(sql, Integer.class, usuarioId);
  }

  private int filasDe(UUID id) {
    return contar("select count(*) from segundo_factor where usuario_id = ?", id)
        + contar("select count(*) from codigo_de_respaldo where usuario_id = ?", id)
        + contar("select count(*) from desafio_segundo_factor where usuario_id = ?", id)
        + jdbc.queryForObject(
            "select count(*) from limite_intentos where clave like ?",
            Integer.class,
            "%segundo-factor:" + id + "%")
        + contar(
            "select count(*) from sesion_refresco where usuario_id = ? and revocado_en is null",
            id);
  }

  @Test
  void borraElFactorLosCodigosLosDesafiosYLosConteosYRevocaLasSesiones() throws IOException {
    Usuario admin = adminConTodo();
    Usuario otro = adminConTodo();
    assertThat(contar("select count(*) from codigo_de_respaldo where usuario_id = ?", admin.id()))
        .isEqualTo(SegundoFactor.CANTIDAD_DE_CODIGOS);

    jdbc.execute(procedimiento().replace(MARCADOR, admin.correo().valor().toUpperCase()));

    assertThat(filasDe(admin.id())).isZero();
    assertThat(contar("select count(*) from sesion_refresco where usuario_id = ?", admin.id()))
        .isEqualTo(1);
    // El otro administrador no se toca.
    assertThat(filasDe(otro.id())).isEqualTo(SegundoFactor.CANTIDAD_DE_CODIGOS + 1 + 1 + 2 + 1);
  }

  @Test
  void conElMarcadorSinReemplazarNoTocaNada() throws IOException {
    Usuario admin = adminConTodo();
    int antes = filasDe(admin.id());

    assertThatThrownBy(() -> jdbc.execute(procedimiento())).hasMessageContaining("no rows");

    assertThat(filasDe(admin.id())).isEqualTo(antes);
  }

  @Test
  void unCorreoQueNoEsDeUnAdminNoTocaNada() throws IOException {
    Usuario cliente =
        Usuario.crear(
            new CorreoElectronico("cliente-" + UUID.randomUUID() + "@tecnosport.co"),
            "hash",
            Rol.CLIENTE,
            Instant.now());
    usuarios.guardar(cliente);

    assertThatThrownBy(
            () -> jdbc.execute(procedimiento().replace(MARCADOR, cliente.correo().valor())))
        .hasMessageContaining("no rows");
  }
}
