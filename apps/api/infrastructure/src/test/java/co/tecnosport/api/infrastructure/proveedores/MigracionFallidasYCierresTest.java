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
 * V106 saca las fallidas de los descartes en los lotes de antes, y V107 pasa a código el motivo en
 * español de cada publicación. Con la forma de la ingesta de Meraki del 11 de octubre de 2026: un
 * lote terminado que contó como descartadas las publicaciones que la API rechazó.
 */
@Testcontainers
class MigracionFallidasYCierresTest {

  @Container
  static PostgreSQLContainer postgres =
      new PostgreSQLContainer(DockerImageName.parse("postgres:16"));

  private static final Instant T = Instant.parse("2026-10-11T19:35:00Z");

  private DriverManagerDataSource fuente;
  private JdbcTemplate jdbc;
  private UUID proveedor;
  private UUID lote;
  private int mensajes;

  private Flyway flywayHasta(String version) {
    return Flyway.configure()
        .dataSource(fuente)
        .locations("classpath:db/migration")
        .target(version)
        .cleanDisabled(false)
        .load();
  }

  @BeforeEach
  void unLoteTerminadoHastaV105() {
    fuente =
        new DriverManagerDataSource(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    jdbc = new JdbcTemplate(fuente);
    Flyway flyway = flywayHasta("105");
    flyway.clean();
    flyway.migrate();
    proveedor = UUID.randomUUID();
    jdbc.update(
        "insert into proveedor (id, nombre, linea, telefono_whatsapp, nombre_en_exportacion,"
            + " activo, publicacion_automatica, creado_en, actualizado_en, orden_de_publicacion)"
            + " values (?, 'Meraki', 'ROPA', '+57 300', 'Meraki', true, false, ?, ?,"
            + " 'FOTOS_PRIMERO')",
        proveedor,
        Timestamp.from(T),
        Timestamp.from(T));
    lote = UUID.randomUUID();
    jdbc.update(
        "insert into lote_ingesta (id, origen, proveedor_id, referencia_archivo, estado,"
            + " resumen_mensajes_leidos, resumen_mensajes_ignorados, resumen_mensajes_nuevos,"
            + " resumen_publicaciones, resumen_borradores_nuevos, resumen_renovaciones,"
            + " resumen_agotados, resumen_descartes, resumen_alertas, creado_en, terminado_en)"
            + " values (?, 'EXPORTACION_CHAT', ?, 'p/a.zip', 'TERMINADO', 10, 0, 10, 4, 0, 0, 0,"
            + " 4, 0, ?, ?)",
        lote,
        proveedor,
        Timestamp.from(T),
        Timestamp.from(T));
  }

  private UUID publicacion(String estado, String motivo) {
    UUID mensaje = UUID.randomUUID();
    jdbc.update(
        "insert into mensaje_proveedor (id, proveedor_id, lote_id, id_externo, enviado_en, tipo,"
            + " texto, medio_omitido, creado_en) values (?, ?, ?, ?, ?, 'TEXTO', 'Polo 1.1',"
            + " false, ?)",
        mensaje,
        proveedor,
        lote,
        "m" + (++mensajes),
        Timestamp.from(T),
        Timestamp.from(T));
    UUID id = UUID.randomUUID();
    jdbc.update(
        "insert into publicacion_proveedor (id, proveedor_id, lote_id, mensaje_principal_id,"
            + " fecha, estado, motivo, creado_en) values (?, ?, ?, ?, ?, ?, ?, ?)",
        id,
        proveedor,
        lote,
        mensaje,
        Timestamp.from(T),
        estado,
        motivo,
        Timestamp.from(T));
    return id;
  }

  private Map<String, Object> publicacion(UUID id) {
    return jdbc.queryForMap(
        "select codigo_motivo, dato_motivo, motivo from publicacion_proveedor where id = ?", id);
  }

  @Test
  void lasFallidasSalenDeLosDescartes() {
    publicacion("ERROR", "La API respondió 400: Your credit balance is too low.");
    publicacion("ERROR", "Error inesperado (NullPointerException).");
    publicacion("DESCARTADA", "El extractor no reconoció un producto en el mensaje.");
    publicacion("EXTRAIDA", null);

    flywayHasta("107").migrate();

    Map<String, Object> resumen =
        jdbc.queryForMap(
            "select resumen_descartes, resumen_fallidas from lote_ingesta where id = ?", lote);
    assertThat(resumen).containsEntry("resumen_fallidas", 2).containsEntry("resumen_descartes", 2);
  }

  @Test
  void cadaMotivoConocidoPasaASuCodigoYLasFallidasConservanElDetalle() {
    UUID caballero =
        publicacion(
            "DESCARTADA",
            "El anuncio viene en el chat de caballero del proveedor; se procesa desde ese archivo.");
    UUID sumadas =
        publicacion(
            "DESCARTADA",
            "Es la misma referencia de un borrador que ya está en revisión; sus 3 fotos se sumaron"
                + " a ese borrador.");
    UUID repetido =
        publicacion(
            "DESCARTADA",
            "Es el mismo anuncio, con la misma foto, de un borrador que ya está en revisión.");
    UUID sinSaldo = publicacion("ERROR", "La API respondió 400: Your credit balance is too low.");
    UUID inesperado = publicacion("ERROR", "Error inesperado (NullPointerException).");
    UUID deDominio = publicacion("ERROR", "La talla no puede estar vacia.");
    UUID raro = publicacion("DESCARTADA", "Algo que nadie escribió así.");

    flywayHasta("107").migrate();

    assertThat(publicacion(caballero)).containsEntry("codigo_motivo", "VIENE_EN_CHAT_DE_CABALLERO");
    assertThat(publicacion(sumadas))
        .containsEntry("codigo_motivo", "MISMA_REFERENCIA_EN_REVISION")
        .containsEntry("dato_motivo", "3");
    assertThat(publicacion(repetido)).containsEntry("codigo_motivo", "ANUNCIO_REPETIDO");
    assertThat(publicacion(sinSaldo))
        .containsEntry("codigo_motivo", "EXTRACCION_FALLIDA")
        .containsEntry("motivo", "La API respondió 400: Your credit balance is too low.");
    assertThat(publicacion(inesperado))
        .containsEntry("codigo_motivo", "ERROR_AL_RESOLVER")
        .containsEntry("dato_motivo", "NullPointerException");
    assertThat(publicacion(raro)).containsEntry("codigo_motivo", "DESCONOCIDO");
    assertThat(publicacion(deDominio))
        .as("una regla del dominio al resolver no es la API")
        .containsEntry("codigo_motivo", "ERROR_AL_RESOLVER");
  }

  @Test
  void despuesDeLaMigracionUnaDescartadaSinCodigoNoEntra() {
    flywayHasta("107").migrate();

    assertThatThrownBy(() -> publicacion("DESCARTADA", "con texto pero sin código"))
        .hasMessageContaining("ck_publicacion_proveedor_motivo");
  }
}
