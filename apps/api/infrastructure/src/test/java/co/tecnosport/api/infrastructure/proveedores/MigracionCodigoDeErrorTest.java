package co.tecnosport.api.infrastructure.proveedores;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
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
 * V104 convierte las frases en español que guardaban los lotes en error en el código que el panel
 * traduce. Se prueba con las frases tal como las escribía el código hasta el 11 de octubre de 2026:
 * la base hasta V103, los lotes viejos, y después V104.
 */
@Testcontainers
class MigracionCodigoDeErrorTest {

  @Container
  static PostgreSQLContainer postgres =
      new PostgreSQLContainer(DockerImageName.parse("postgres:16"));

  private static final Instant T = Instant.parse("2026-10-11T06:17:00Z");

  private DriverManagerDataSource fuente;
  private JdbcTemplate jdbc;
  private UUID proveedor;

  private Flyway flywayHasta(String version) {
    return Flyway.configure()
        .dataSource(fuente)
        .locations("classpath:db/migration")
        .target(version)
        .cleanDisabled(false)
        .load();
  }

  @BeforeEach
  void hastaV103() {
    fuente =
        new DriverManagerDataSource(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    jdbc = new JdbcTemplate(fuente);
    Flyway flyway = flywayHasta("103");
    flyway.clean();
    flyway.migrate();
    proveedor = UUID.randomUUID();
    jdbc.update(
        "insert into proveedor (id, nombre, linea, telefono_whatsapp, nombre_en_exportacion,"
            + " activo, publicacion_automatica, creado_en, actualizado_en, orden_de_publicacion)"
            + " values (?, 'Violeta', 'ROPA', '+57 300', 'Violeta', true, false, ?, ?,"
            + " 'FOTOS_PRIMERO')",
        proveedor,
        Timestamp.from(T),
        Timestamp.from(T));
  }

  private UUID enError(String frase) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "insert into lote_ingesta (id, origen, proveedor_id, referencia_archivo, estado,"
            + " detalle_error, creado_en, terminado_en)"
            + " values (?, 'EXPORTACION_CHAT', ?, 'p/a.zip', 'ERROR', ?, ?, ?)",
        id,
        proveedor,
        frase,
        Timestamp.from(T),
        Timestamp.from(T));
    return id;
  }

  private Map<String, Object> fila(UUID id) {
    return jdbc.queryForMap(
        "select codigo_error, detalle_error from lote_ingesta where id = ?", id);
  }

  @Test
  void lasFrasesConocidasPasanASuCodigoYElDatoQueLlevabanQuedaAparte() {
    UUID reinicio =
        enError(
            "La aplicación se reinició mientras se procesaba o estaba en pausa. Para procesarla"
                + " entera, elimina la ingesta y vuelve a subir la exportación.");
    UUID colaLlena = enError("La cola de ingestas estaba llena. Vuelve a subir la exportación.");
    UUID sinChat =
        enError(
            "El zip no trae ningún archivo .txt con el chat. Exporta el chat de nuevo desde"
                + " WhatsApp, con o sin archivos.");
    UUID fecha = enError("Una fecha de la exportación no se pudo leer: 31/2/26");
    UUID inesperado = enError("Error inesperado (NullPointerException).");
    UUID raro = enError("Algo que nadie escribió con esta forma.");

    flywayHasta("104").migrate();

    assertThat(fila(reinicio)).containsEntry("codigo_error", "REINICIO");
    assertThat(fila(reinicio).get("detalle_error")).isNull();
    assertThat(fila(colaLlena)).containsEntry("codigo_error", "COLA_LLENA");
    assertThat(fila(sinChat)).containsEntry("codigo_error", "ZIP_SIN_CHAT");
    assertThat(fila(fecha))
        .containsEntry("codigo_error", "FECHA_ILEGIBLE")
        .containsEntry("detalle_error", "31/2/26");
    assertThat(fila(inesperado))
        .containsEntry("codigo_error", "ERROR_INESPERADO")
        .containsEntry("detalle_error", "NullPointerException");
    assertThat(fila(raro))
        .as("lo que no se reconoce queda DESCONOCIDO, con su frase guardada y sin mostrarse")
        .containsEntry("codigo_error", "DESCONOCIDO")
        .containsEntry("detalle_error", "Algo que nadie escribió con esta forma.");
  }

  /** Desde V104, un lote en error tiene código; el dato es opcional. */
  @Test
  void despuesDeLaMigracionUnLoteEnErrorSinCodigoNoEntra() {
    flywayHasta("104").migrate();

    assertThatThrownBy(() -> enError(null))
        .hasMessageContaining("ck_lote_ingesta_error_con_codigo");
    UUID conCodigo = UUID.randomUUID();
    jdbc.update(
        "insert into lote_ingesta (id, origen, proveedor_id, referencia_archivo, estado,"
            + " codigo_error, creado_en, terminado_en)"
            + " values (?, 'EXPORTACION_CHAT', ?, 'p/a.zip', 'ERROR', 'COLA_LLENA', ?, ?)",
        conCodigo,
        proveedor,
        Timestamp.from(T),
        Timestamp.from(T));
    assertThat(fila(conCodigo)).containsEntry("codigo_error", "COLA_LLENA");
  }
}
