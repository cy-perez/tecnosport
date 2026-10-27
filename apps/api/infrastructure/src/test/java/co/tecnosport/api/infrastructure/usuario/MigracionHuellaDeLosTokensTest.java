package co.tecnosport.api.infrastructure.usuario;

import static org.assertj.core.api.Assertions.assertThat;

import co.tecnosport.api.domain.usuario.HuellaDeToken;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * V140: los tokens pasan de guardarse en claro a guardarse por su SHA-256. Las filas que ya
 * existían se convierten en SQL, y la sesión viva solo sobrevive si esa huella es exactamente la
 * que calcula {@link HuellaDeToken} en Java: son dos copias de la misma fórmula, y si una cambia
 * sin la otra, el despliegue cierra todas las sesiones y vence todos los enlaces enviados. Esto lo
 * comprueba contra PostgreSQL de verdad.
 */
@Testcontainers
class MigracionHuellaDeLosTokensTest {

  @Container
  static PostgreSQLContainer postgres =
      new PostgreSQLContainer(DockerImageName.parse("postgres:16"));

  private static final List<String> TABLAS =
      List.of("sesion_refresco", "token_recuperacion_clave", "token_verificacion_correo");

  private DriverManagerDataSource fuente;
  private JdbcTemplate jdbc;
  private UUID usuario;

  private Flyway flywayHasta(String version) {
    return Flyway.configure()
        .dataSource(fuente)
        .locations("classpath:db/migration")
        .target(version)
        .cleanDisabled(false)
        .load();
  }

  @BeforeEach
  void antesDeV140() {
    fuente =
        new DriverManagerDataSource(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    jdbc = new JdbcTemplate(fuente);
    // La última de main cuando se escribió V140: el objetivo tiene que existir, y lo que haya
    // entre las dos no toca estas tablas.
    Flyway flyway = flywayHasta("124");
    flyway.clean();
    flyway.migrate();
    usuario = UUID.randomUUID();
    jdbc.update(
        "insert into usuario (id, correo, clave_hash, rol, creado_en)"
            + " values (?, 'titular@tecnosport.co', 'hash', 'CLIENTE', now())",
        usuario);
  }

  @Test
  void lasFilasQueYaExistianQuedanConLaHuellaQueCalculaJava() {
    UUID sesion = UUID.randomUUID();
    UUID recuperacion = UUID.randomUUID();
    UUID verificacion = UUID.randomUUID();
    jdbc.update(
        "insert into sesion_refresco (id, usuario_id, familia_id, creado_en, expira_en)"
            + " values (?, ?, ?, now(), now() + interval '30 days')",
        sesion,
        usuario,
        UUID.randomUUID());
    jdbc.update(
        "insert into token_recuperacion_clave (id, usuario_id, creado_en, expira_en)"
            + " values (?, ?, now(), now() + interval '30 minutes')",
        recuperacion,
        usuario);
    jdbc.update(
        "insert into token_verificacion_correo (id, usuario_id, creado_en, expira_en)"
            + " values (?, ?, now(), now() + interval '1 day')",
        verificacion,
        usuario);

    flywayHasta("140").migrate();

    assertThat(huellas("sesion_refresco")).containsExactly(HuellaDeToken.de(sesion));
    assertThat(huellas("token_recuperacion_clave")).containsExactly(HuellaDeToken.de(recuperacion));
    assertThat(huellas("token_verificacion_correo"))
        .containsExactly(HuellaDeToken.de(verificacion));
  }

  @Test
  void ningunaTablaGuardaYaElTokenEnClaro() {
    flywayHasta("140").migrate();

    for (String tabla : TABLAS) {
      Integer columnasId =
          jdbc.queryForObject(
              "select count(*) from information_schema.columns"
                  + " where table_name = ? and column_name = 'id'",
              Integer.class,
              tabla);
      assertThat(columnasId).as(tabla).isZero();
    }
  }

  private List<String> huellas(String tabla) {
    return jdbc.queryForList("select huella from " + tabla, String.class);
  }
}
