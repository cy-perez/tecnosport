package co.tecnosport.api.infrastructure.proveedores;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
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
 * V122 (26 de octubre de 2026): La Riverah y Violeta son locales y los demás no. Se prueba con la
 * base hasta V121, los proveedores como están en la nube, y después V122.
 */
@Testcontainers
class MigracionProveedorLocalTest {

  @Container
  static PostgreSQLContainer postgres =
      new PostgreSQLContainer(DockerImageName.parse("postgres:16"));

  private static final Instant T = Instant.parse("2026-10-26T06:00:00Z");

  private DriverManagerDataSource fuente;
  private JdbcTemplate jdbc;

  private Flyway flywayHasta(String version) {
    return Flyway.configure()
        .dataSource(fuente)
        .locations("classpath:db/migration")
        .target(version)
        .cleanDisabled(false)
        .load();
  }

  @BeforeEach
  void hastaV121() {
    fuente =
        new DriverManagerDataSource(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    jdbc = new JdbcTemplate(fuente);
    Flyway flyway = flywayHasta("121");
    flyway.clean();
    flyway.migrate();
  }

  private UUID proveedor(String nombre, String linea) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "insert into proveedor (id, nombre, linea, telefono_whatsapp, nombre_en_exportacion,"
            + " activo, publicacion_automatica, creado_en, actualizado_en, orden_de_publicacion)"
            + " values (?, ?, ?, '+57 300', ?, true, false, ?, ?, 'TEXTO_PRIMERO')",
        id,
        nombre,
        linea,
        nombre,
        Timestamp.from(T),
        Timestamp.from(T));
    return id;
  }

  private boolean esLocal(UUID id) {
    return jdbc.queryForObject("select es_local from proveedor where id = ?", Boolean.class, id);
  }

  @Test
  void soloLaRiverahYVioletaSonLocales() {
    UUID riverah = proveedor("La Riverah", "ROPA");
    UUID violeta = proveedor("VIOLETA", "BOLSOS");
    UUID meraki = proveedor("Meraki", "ROPA");
    UUID tenis = proveedor("Tenis Al Pie", "CALZADO");
    UUID tecnologia = proveedor("Mayorista", "TECNOLOGIA");

    flywayHasta("122").migrate();

    assertThat(esLocal(riverah)).isTrue();
    assertThat(esLocal(violeta)).isTrue();
    assertThat(esLocal(meraki)).isFalse();
    assertThat(esLocal(tenis)).isFalse();
    assertThat(esLocal(tecnologia)).isFalse();
  }
}
