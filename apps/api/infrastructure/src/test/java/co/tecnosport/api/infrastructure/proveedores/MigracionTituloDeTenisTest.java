package co.tecnosport.api.infrastructure.proveedores;

import static org.assertj.core.api.Assertions.assertThat;

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
 * V121 (25 de octubre de 2026): el calzado publicado toma el título de un tenis, sin el género
 * (ADR-0089). Se prueba con la base hasta V120, los títulos que el extractor escribía, y después
 * V121.
 */
@Testcontainers
class MigracionTituloDeTenisTest {

  @Container
  static PostgreSQLContainer postgres =
      new PostgreSQLContainer(DockerImageName.parse("postgres:16"));

  private DriverManagerDataSource fuente;
  private JdbcTemplate jdbc;
  private UUID marca;

  private Flyway flywayHasta(String version) {
    return Flyway.configure()
        .dataSource(fuente)
        .locations("classpath:db/migration")
        .target(version)
        .cleanDisabled(false)
        .load();
  }

  @BeforeEach
  void hastaV120() {
    fuente =
        new DriverManagerDataSource(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    jdbc = new JdbcTemplate(fuente);
    Flyway flyway = flywayHasta("120");
    flyway.clean();
    flyway.migrate();
    marca = UUID.randomUUID();
    jdbc.update("insert into marca (id, nombre, creado_en) values (?, 'Genérica', now())", marca);
  }

  private UUID producto(String nombre, String slugDeCategoria) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "insert into producto (id, nombre, slug, descripcion, marca_id, categoria_id, estado,"
            + " origen, estado_disponibilidad, creado_en, actualizado_en)"
            + " select ?, ?, ?, '', ?, c.id, 'PUBLICADO', 'MANUAL', 'DISPONIBLE', now(), now()"
            + " from categoria c where c.slug = ?",
        id,
        nombre,
        "p-" + id,
        marca,
        slugDeCategoria);
    return id;
  }

  private String nombre(UUID producto) {
    return jdbc.queryForObject("select nombre from producto where id = ?", String.class, producto);
  }

  @Test
  void elCalzadoTomaElTituloDeUnTenisSinElGenero() {
    UUID dama = producto("Tenis estilo Nike para dama", "calzado-dama");
    UUID caballero = producto("Tenis estilo Adidas Superstar importado", "calzado-caballero");
    UUID sinMarca = producto("Tenis para caballero tipo media ultraliviano", "calzado-caballero");
    UUID yaNuevo = producto("Tenis importados estilo Nike Air Force 1", "calzado-unisex");
    UUID bolso = producto("Tenis estilo Nike para dama", "bolsos-dama-bolsos-de-mano");

    flywayHasta("121").migrate();

    assertThat(nombre(dama)).isEqualTo("Tenis importados estilo Nike");
    assertThat(nombre(caballero)).isEqualTo("Tenis importados estilo Adidas Superstar");
    assertThat(nombre(sinMarca)).isEqualTo("Tenis importados");
    assertThat(nombre(yaNuevo)).isEqualTo("Tenis importados estilo Nike Air Force 1");
    assertThat(nombre(bolso))
        .as("fuera del calzado no se toca")
        .isEqualTo("Tenis estilo Nike para dama");
  }
}
