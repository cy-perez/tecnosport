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
 * V113 (12 de octubre de 2026): La Riverah arma sets, la errata «Falta - Short» de la nube se
 * corrige, una base nueva gana la categoría, y los conjuntos pesan 700 g. Se prueba con la base
 * hasta V112, los datos como están en la nube, y después V113.
 */
@Testcontainers
class MigracionSetsYFaldaShortTest {

  @Container
  static PostgreSQLContainer postgres =
      new PostgreSQLContainer(DockerImageName.parse("postgres:16"));

  private static final Instant T = Instant.parse("2026-10-12T06:00:00Z");

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
  void hastaV112() {
    fuente =
        new DriverManagerDataSource(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    jdbc = new JdbcTemplate(fuente);
    Flyway flyway = flywayHasta("112");
    flyway.clean();
    flyway.migrate();
  }

  private void migrarV113() {
    flywayHasta("113").migrate();
  }

  private UUID proveedor(String nombre) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "insert into proveedor (id, nombre, linea, telefono_whatsapp, nombre_en_exportacion,"
            + " activo, publicacion_automatica, creado_en, actualizado_en, orden_de_publicacion)"
            + " values (?, ?, 'ROPA', '+57 300', ?, true, false, ?, ?, 'TEXTO_PRIMERO')",
        id,
        nombre,
        nombre,
        Timestamp.from(T),
        Timestamp.from(T));
    return id;
  }

  /** La categoría como se creó en la nube desde el panel, con la errata en el nombre y el slug. */
  private UUID faltaShortDeLaNube(int peso) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "insert into categoria (id, nombre, slug, linea, padre_id, creado_en)"
            + " select ?, 'Falta - Short', 'ropa-dama-falta-short', p.linea, p.id, now()"
            + " from categoria p where p.slug = 'ropa-dama'",
        id);
    jdbc.update(
        "insert into envio_peso_referencia (categoria_id, peso_gramos) values (?, ?)", id, peso);
    return id;
  }

  private int contar(String sql, Object... args) {
    return jdbc.queryForObject(sql, Integer.class, args);
  }

  @Test
  void soloLaRiverahArmaSets() {
    UUID riverah = proveedor("La Riverah");
    UUID violeta = proveedor("Violeta");

    migrarV113();

    assertThat(
            jdbc.queryForObject(
                "select arma_sets from proveedor where id = ?", Boolean.class, riverah))
        .isTrue();
    assertThat(
            jdbc.queryForObject(
                "select arma_sets from proveedor where id = ?", Boolean.class, violeta))
        .isFalse();
  }

  @Test
  void laErrataDeLaNubeSeCorrigeYConservaSuPeso() {
    UUID categoria = faltaShortDeLaNube(450);

    migrarV113();

    assertThat(jdbc.queryForMap("select nombre, slug from categoria where id = ?", categoria))
        .containsEntry("nombre", "Falda - Short")
        .containsEntry("slug", "ropa-dama-falda-short");
    assertThat(contar("select count(*) from categoria where slug like 'ropa-dama-fal%-short'"))
        .isEqualTo(1);
    assertThat(
            contar(
                "select peso_gramos from envio_peso_referencia where categoria_id = ?", categoria))
        .as("el peso que alguien le puso desde el panel no se toca")
        .isEqualTo(450);
  }

  @Test
  void enUnaBaseNuevaLaFaldaShortSeCreaConSuPeso() {
    migrarV113();

    assertThat(
            contar(
                "select p.peso_gramos from envio_peso_referencia p"
                    + " join categoria c on c.id = p.categoria_id"
                    + " where c.slug = 'ropa-dama-falda-short'"))
        .isEqualTo(400);
    assertThat(
            contar(
                "select count(*) from categoria c join categoria p on p.id = c.padre_id"
                    + " where c.slug = 'ropa-dama-falda-short' and p.slug = 'ropa-dama'"))
        .isEqualTo(1);
  }

  @Test
  void losConjuntosPesanSetecientosGramosAunqueYaTuvieranOtroPeso() {
    UUID dama =
        jdbc.queryForObject(
            "select id from categoria where slug = 'ropa-dama-conjuntos'", UUID.class);
    jdbc.update(
        "insert into envio_peso_referencia (categoria_id, peso_gramos) values (?, 500)", dama);

    migrarV113();

    assertThat(
            contar(
                "select p.peso_gramos from envio_peso_referencia p"
                    + " join categoria c on c.id = p.categoria_id"
                    + " where c.slug = 'ropa-dama-conjuntos'"))
        .isEqualTo(700);
    assertThat(
            contar(
                "select p.peso_gramos from envio_peso_referencia p"
                    + " join categoria c on c.id = p.categoria_id"
                    + " where c.slug = 'ropa-caballero-conjuntos'"))
        .isEqualTo(700);
  }

  @Test
  void losProductosNacenSinPesoAdicionalYLosBorradoresSinSet() {
    migrarV113();

    assertThat(
            contar(
                "select count(*) from information_schema.columns where table_name = 'producto'"
                    + " and column_name = 'peso_adicional_gramos' and column_default = '0'"))
        .isEqualTo(1);
    assertThat(
            contar(
                "select count(*) from information_schema.columns"
                    + " where table_name = 'borrador_producto'"
                    + " and column_name in ('set_acompanante', 'set_origen_del_costo')"))
        .isEqualTo(2);
  }
}
