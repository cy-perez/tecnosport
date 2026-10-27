package co.tecnosport.api.infrastructure.pedido;

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
 * V124 (27 de octubre de 2026, ADR-0095): la línea del pedido guarda el precio publicado además del
 * que se cobra. Las líneas de antes se cobraron al único precio que había, así que su publicado es
 * el mismo y su descuento, cero. Se prueba con la base hasta V123, una línea sembrada, y después
 * V124.
 */
@Testcontainers
class MigracionPrecioPublicadoEnLaLineaTest {

  @Container
  static PostgreSQLContainer postgres =
      new PostgreSQLContainer(DockerImageName.parse("postgres:16"));

  private DriverManagerDataSource fuente;
  private JdbcTemplate jdbc;
  private UUID pedido;

  private Flyway flywayHasta(String version) {
    return Flyway.configure()
        .dataSource(fuente)
        .locations("classpath:db/migration")
        .target(version)
        .cleanDisabled(false)
        .load();
  }

  @BeforeEach
  void hastaV123() {
    fuente =
        new DriverManagerDataSource(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    jdbc = new JdbcTemplate(fuente);
    Flyway flyway = flywayHasta("123");
    flyway.clean();
    flyway.migrate();
    pedido = UUID.randomUUID();
    jdbc.update(
        "insert into pedido (id, numero_pedido, correo, tipo_entrega, metodo_pago, estado,"
            + " costo_envio,"
            + " creado_en) values (?, 'TS-2026-000124', 'cliente@tecnosport.co',"
            + " 'RETIRO_EN_PUNTO', 'TRANSFERENCIA_MANUAL', 'PAGO_PENDIENTE', 0, now())",
        pedido);
  }

  private UUID linea(String sku, long precio) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "insert into linea_pedido (id, pedido_id, variante_id, sku, nombre, cantidad,"
            + " precio_unitario, tasa_iva, id_reserva) values (?, ?, ?, ?, 'Bolso', 2, ?, 0, ?)",
        id,
        pedido,
        UUID.randomUUID(),
        sku,
        BigDecimal.valueOf(precio),
        UUID.randomUUID());
    return id;
  }

  @Test
  void lasLineasDeAntesQuedanConElPublicadoIgualAlCobrado() {
    UUID barata = linea("V124-1", 89_900);
    UUID cara = linea("V124-2", 1_000_000);

    flywayHasta("124").migrate();

    for (UUID id : new UUID[] {barata, cara}) {
      BigDecimal cobrado =
          jdbc.queryForObject(
              "select precio_unitario from linea_pedido where id = ?", BigDecimal.class, id);
      BigDecimal publicado =
          jdbc.queryForObject(
              "select precio_publicado from linea_pedido where id = ?", BigDecimal.class, id);
      assertThat(publicado).isEqualByComparingTo(cobrado);
    }
  }

  @Test
  void despuesDeLaMigracionNoSeCobraPorEncimaDelPublicado() {
    UUID id = linea("V124-CHECK", 230_000);
    flywayHasta("124").migrate();

    assertThatThrownBy(
            () ->
                jdbc.update(
                    "update linea_pedido set precio_unitario = precio_publicado + 1 where id = ?",
                    (Object) id))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void despuesDeLaMigracionElPublicadoEsObligatorio() {
    flywayHasta("124").migrate();

    assertThatThrownBy(() -> linea("V124-SIN-PUBLICADO", 50_000))
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
