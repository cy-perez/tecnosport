package co.tecnosport.api.infrastructure.catalogo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.tecnosport.api.domain.catalogo.ComisionDePasarela;
import co.tecnosport.api.domain.compartido.Dinero;
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
 * V123 (26 de octubre de 2026, ADR-0095): los precios que había eran de contado, y el publicado se
 * calcula con las tarifas de Wompi de ese día. Se prueba con la base hasta V122, variantes con los
 * precios de los ejemplos del ADR, y después V123. El publicado que deja el SQL se compara contra
 * el que calcula {@link ComisionDePasarela}: son dos copias de la misma fórmula, y si una cambia
 * sin la otra, esto falla.
 */
@Testcontainers
class MigracionPrecioDeContadoTest {

  @Container
  static PostgreSQLContainer postgres =
      new PostgreSQLContainer(DockerImageName.parse("postgres:16"));

  /** Las que la V123 lleva escritas a mano: las del plan de Wompi del 26 de octubre de 2026. */
  private static final ComisionDePasarela TARIFAS_DE_LA_MIGRACION =
      new ComisionDePasarela(new BigDecimal("0.0265"), Dinero.deCop(700), new BigDecimal("0.19"));

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
  void hastaV122() {
    fuente =
        new DriverManagerDataSource(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    jdbc = new JdbcTemplate(fuente);
    Flyway flyway = flywayHasta("122");
    flyway.clean();
    flyway.migrate();
    UUID marca = UUID.randomUUID();
    jdbc.update("insert into marca (id, nombre, creado_en) values (?, 'Genérica', now())", marca);
    producto = UUID.randomUUID();
    jdbc.update(
        "insert into producto (id, nombre, slug, descripcion, marca_id, categoria_id, estado,"
            + " origen, estado_disponibilidad, creado_en, actualizado_en)"
            + " select ?, 'Bolso', 'bolso-v123', '', ?, c.id, 'PUBLICADO', 'MANUAL', 'DISPONIBLE',"
            + " now(), now() from categoria c where c.slug = 'bolsos-dama-bolsos-de-mano'",
        producto,
        marca);
  }

  private UUID variante(String sku, long precio) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "insert into variante (id, producto_id, sku, precio, tasa_iva, estado, creado_en)"
            + " values (?, ?, ?, ?, 0, 'ACTIVA', now())",
        id,
        producto,
        sku,
        BigDecimal.valueOf(precio));
    return id;
  }

  private BigDecimal columna(String columna, UUID variante) {
    return jdbc.queryForObject(
        "select " + columna + " from variante where id = ?", BigDecimal.class, variante);
  }

  @Test
  void elPrecioDeHoyPasaAContadoYElPublicadoEsElDeJava() {
    long[] precios = {230_000, 1_000_000, 100_000, 50_000, 8_000, 96_000, 96_100, 89_900, 1};
    UUID[] ids = new UUID[precios.length];
    for (int i = 0; i < precios.length; i++) {
      ids[i] = variante("V123-" + i, precios[i]);
    }

    flywayHasta("123").migrate();

    for (int i = 0; i < precios.length; i++) {
      Dinero contado = Dinero.deCop(precios[i]);
      assertThat(columna("precio_contado", ids[i])).isEqualByComparingTo(contado.valor());
      assertThat(columna("precio", ids[i]))
          .as("publicado de %d", precios[i])
          .isEqualByComparingTo(TARIFAS_DE_LA_MIGRACION.publicadoDesde(contado).valor());
    }
    // Los cuatro del ADR, escritos: si la fórmula de Java también se equivocara, esto no.
    assertThat(columna("precio", ids[0])).isEqualByComparingTo("239000");
    assertThat(columna("precio", ids[1])).isEqualByComparingTo("1034000");
    assertThat(columna("precio", ids[2])).isEqualByComparingTo("105000");
    assertThat(columna("precio", ids[3])).isEqualByComparingTo("52500");
    // Por debajo de 100.000 a la centena, desde ahí a miles: los dos lados del umbral.
    assertThat(columna("precio", ids[4])).isEqualByComparingTo("9200");
    assertThat(columna("precio", ids[5])).isEqualByComparingTo("100000");
    assertThat(columna("precio", ids[6])).isEqualByComparingTo("101000");
  }

  @Test
  void despuesDeLaMigracionLaBaseNoAceptaUnPublicadoMenorQueElContado() {
    UUID id = variante("V123-CHECK", 230_000);
    flywayHasta("123").migrate();

    assertThatThrownBy(
            () ->
                jdbc.update(
                    "update variante set precio = precio_contado - 1 where id = ?", (Object) id))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void despuesDeLaMigracionElContadoEsObligatorio() {
    flywayHasta("123").migrate();

    assertThatThrownBy(
            () ->
                jdbc.update(
                    "insert into variante (id, producto_id, sku, precio, tasa_iva, estado,"
                        + " creado_en) values (?, ?, 'SIN-CONTADO', 1000, 0, 'ACTIVA', now())",
                    UUID.randomUUID(),
                    producto))
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
