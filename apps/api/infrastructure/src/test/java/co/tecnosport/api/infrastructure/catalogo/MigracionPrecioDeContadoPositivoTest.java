package co.tecnosport.api.infrastructure.catalogo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * V135 (27 de octubre de 2026): el precio de contado es mayor que cero. Con la base hasta V124 —la
 * última que existía al escribirla; Flyway no acepta un objetivo que no exista— se siembran
 * variantes y se aplica V135: si alguna está en cero la migración falla nombrándola, en vez de
 * inventarle un precio; si no, la restricción queda puesta.
 */
@Testcontainers
class MigracionPrecioDeContadoPositivoTest {

  @Container
  static PostgreSQLContainer postgres =
      new PostgreSQLContainer(DockerImageName.parse("postgres:16"));

  private DriverManagerDataSource fuente;
  private JdbcTemplate jdbc;
  private UUID producto;

  private Flyway flywayHasta(String version) {
    return Flyway.configure()
        .dataSource(fuente)
        .locations("classpath:db/migration")
        .target(version)
        .cleanDisabled(false)
        .load();
  }

  @BeforeEach
  void hastaAntesDeV135() {
    fuente =
        new DriverManagerDataSource(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    jdbc = new JdbcTemplate(fuente);
    Flyway flyway = flywayHasta("124");
    flyway.clean();
    flyway.migrate();
    UUID marca = UUID.randomUUID();
    jdbc.update("insert into marca (id, nombre, creado_en) values (?, 'Genérica', now())", marca);
    producto = UUID.randomUUID();
    jdbc.update(
        "insert into producto (id, nombre, slug, descripcion, marca_id, categoria_id, estado,"
            + " origen, estado_disponibilidad, creado_en, actualizado_en)"
            + " select ?, 'Bolso', 'bolso-v135', '', ?, c.id, 'PUBLICADO', 'MANUAL', 'DISPONIBLE',"
            + " now(), now() from categoria c where c.slug = 'bolsos-dama-bolsos-de-mano'",
        producto,
        marca);
  }

  private UUID variante(String sku, long contado) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "insert into variante (id, producto_id, sku, precio, precio_contado, tasa_iva, estado,"
            + " creado_en) values (?, ?, ?, ?, ?, 0, 'ACTIVA', now())",
        id,
        producto,
        sku,
        BigDecimal.valueOf(contado + 1_000),
        BigDecimal.valueOf(contado));
    return id;
  }

  @Test
  void conUnaVarianteEnCeroLaMigracionFallaYLaNombra() {
    variante("V135-BIEN", 50_000);
    variante("V135-REGALADA", 0);

    assertThatThrownBy(() -> flywayHasta("135").migrate())
        .hasMessageContaining("V135-REGALADA")
        .hasMessageContaining("no inventa un precio");
    // Y no tocó la fila: el precio lo fija el negocio.
    assertThat(
            jdbc.queryForObject(
                "select precio_contado from variante where sku = 'V135-REGALADA'",
                BigDecimal.class))
        .isEqualByComparingTo(BigDecimal.ZERO);
  }

  @Test
  void despuesDeLaMigracionUnContadoEnCeroNoEntra() {
    UUID id = variante("V135-CHECK", 50_000);
    flywayHasta("135").migrate();

    assertThatThrownBy(
            () -> jdbc.update("update variante set precio_contado = 0 where id = ?", (Object) id))
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
