package co.tecnosport.api.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.core.type.TypeReference;
import tools.jackson.core.util.DefaultIndenter;
import tools.jackson.core.util.DefaultPrettyPrinter;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * El OpenAPI que sirve la aplicación es el que está guardado en {@code
 * packages/contratos/openapi.json}.
 *
 * <p><strong>El guardián del contrato existía y vivía entero en integración continua.</strong> El
 * trabajo {@code contrato} de {@code verificar.yml} levantaba PostgreSQL, arrancaba {@code
 * bootRun}, esperaba hasta cinco minutos a que respondiera, regeneraba {@code tipos.ts} y exigía
 * que el diff quedara vacío. Funcionaba —atrapó el {@code @NotNull} que al quitarse cambió el
 * contrato publicado— pero avisaba <strong>después del empujón y ya sobre la rama</strong>. Es la
 * misma lección que {@code ContextoBajoPerfilE2eTest} dejó escrita al lado: "el único guardián era
 * el flujo de integración continua, seis minutos después del merge y ya sobre {@code main}".
 *
 * <p>Y lo que vigila importa lo bastante como para no enterarse tarde. Un contrato que se mueve sin
 * que nadie lo note costó una vez en la Fase 7 —la migración del paquete por variante agregó cuatro
 * campos, el frontend los mandaba, y ni el lint ni el build ni las 748 pruebas dijeron nada— y
 * volvió el 21 de septiembre por el otro lado: springdoc publicaba {@code disponible} como opcional
 * porque no deduce que un {@code boolean} primitivo siempre se serializa, el cliente lo generaba
 * como {@code disponible?: boolean} y el frontend caía a {@code false}. El día que ese campo dejara
 * de serializarse, <strong>la tienda entera saldría agotada</strong>: todos los botones de comprar
 * deshabilitados, sin una prueba en rojo y sin una línea en el registro.
 *
 * <p>Ahora el guardián son dos eslabones, y ninguno de los dos necesita que alguien levante nada:
 * el código Java produce un OpenAPI —esta prueba, dentro de {@code gradlew build}— y el OpenAPI
 * guardado produce {@code tipos.ts} —{@code tools/verificar-contratos.mjs}, dentro de {@code npm
 * run verificar}—.
 *
 * <p><strong>Por qué MockMvc y no un puerto de verdad.</strong> Con {@code webEnvironment =
 * RANDOM_PORT}, springdoc escribe en {@code servers} la URL por la que le llegó la petición, con el
 * puerto aleatorio dentro: la instantánea cambiaría en cada corrida. Con MockMvc el origen es fijo.
 * Aun así {@code servers} se quita al normalizar, porque describe dónde está desplegada la API y no
 * su contrato — es lo único del documento que depende de quién lo sirve.
 *
 * <p><strong>Por qué se ordenan las llaves.</strong> Nada obliga a springdoc a serializar los
 * caminos y los esquemas en un orden estable entre versiones. Un guardián que falla porque dos
 * llaves cambiaron de sitio no dice nada del contrato, y a la tercera vez se desactiva. Ordenadas,
 * el diff de la instantánea es exactamente lo que cambió.
 *
 * <p><strong>Los perfiles son los mismos que {@code ContextoBajoPerfilE2eTest}</strong> y por el
 * mismo motivo: bajo {@code e2e} los clientes de terceros los sustituyen sus dobles, y pedirle
 * credenciales a una prueba es el escenario que ese perfil evita. El documento no depende del
 * perfil —ningún controlador es condicional, comprobado— así que esto no recorta lo que se vigila.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles({"local", "e2e"})
class ContratoOpenApiTest {

  /**
   * La propiedad que reescribe la instantánea en vez de comparar contra ella. Se pasa como {@code
   * gradlew.bat :bootstrap:test --tests "*ContratoOpenApiTest" -PactualizarContrato=true} y la
   * entrega {@code bootstrap/build.gradle.kts}.
   */
  private static final String ACTUALIZAR = "tecnosport.contrato.actualizar";

  /** La ruta de {@code springdoc.api-docs.path} en {@code application.yml}. */
  private static final String RUTA_OPENAPI = "/api/openapi.json";

  /** El salto de línea de Unix, en una constante para que nadie lo "arregle" a uno del sistema. */
  private static final String LF = "\n";

  @Container @ServiceConnection
  static PostgreSQLContainer postgres =
      new PostgreSQLContainer(DockerImageName.parse("postgres:16"));

  @Autowired private MockMvc mockMvc;

  @Test
  void elContratoGuardadoEsElQueSirveLaApi() throws Exception {
    String servido =
        normalizar(
            mockMvc
                .perform(get(RUTA_OPENAPI))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8));

    Path instantanea = raizDelRepositorio().resolve("packages/contratos/openapi.json");

    // Siempre, también cuando la prueba pasa: si falla, quien la lee tiene el documento servido
    // en un archivo y puede compararlo con su herramienta en vez de leer un diff de una línea.
    Path copia = Path.of("build", "openapi-servido.json");
    Files.createDirectories(copia.getParent());
    Files.writeString(copia, servido, StandardCharsets.UTF_8);

    if (Boolean.parseBoolean(System.getProperty(ACTUALIZAR, "false"))) {
      Files.writeString(instantanea, servido, StandardCharsets.UTF_8);
      System.out.println(
          "Instantánea del contrato reescrita: "
              + instantanea
              + LF
              + "Falta el segundo eslabón: `npm run contratos` regenera tipos.ts desde ella.");
      return;
    }

    assertThat(instantanea)
        .as(
            """
            No hay instantánea del contrato en packages/contratos/openapi.json.

            Se crea con:
              gradlew.bat :bootstrap:test --tests "*ContratoOpenApiTest" -PactualizarContrato=true
            """)
        .exists();

    assertThat(normalizar(Files.readString(instantanea, StandardCharsets.UTF_8)))
        .as(
            """
            El OpenAPI que sirve la aplicación no es el que está guardado.

            Si el cambio del backend es a propósito, el contrato se mueve en dos pasos y los dos
            archivos se commitean juntos:
              1. gradlew.bat :bootstrap:test --tests "*ContratoOpenApiTest" -PactualizarContrato=true
              2. npm run contratos

            Si no lo es, lo que cambió es un DTO o una anotación @Schema sin querer, y el sitio
            donde se arregla es ahí — no en la instantánea.

            El documento servido quedó en apps/api/bootstrap/build/openapi-servido.json.
            """)
        .isEqualTo(servido);
  }

  /**
   * El documento con las llaves ordenadas, sin {@code servers} y con saltos de línea de Unix.
   *
   * <p>Lo último no es cosmético: {@code DefaultPrettyPrinter} usa por omisión el separador de
   * línea del sistema, así que el mismo documento se escribiría con CRLF en Windows y con LF en
   * integración continua, y el guardián fallaría según en qué máquina corriera. Es exactamente lo
   * que le pasó a {@code verificar-kit.mjs} y se resuelve igual: comparando como compara git.
   */
  private static String normalizar(String documento) {
    ObjectMapper mapeador =
        JsonMapper.builder().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).build();
    Map<String, Object> arbol = mapeador.readValue(documento, new MapaDeJson());
    arbol.remove("servers");
    DefaultPrettyPrinter impresor =
        new DefaultPrettyPrinter()
            .withObjectIndenter(new DefaultIndenter("  ", LF))
            .withArrayIndenter(new DefaultIndenter("  ", LF));
    return mapeador.writer().with(impresor).writeValueAsString(arbol) + LF;
  }

  /** El tipo del árbol, que un genérico no puede expresar en línea sin una advertencia. */
  private static final class MapaDeJson extends TypeReference<LinkedHashMap<String, Object>> {}

  /**
   * La raíz del monorepo, subiendo desde el directorio de trabajo de la prueba ({@code
   * apps/api/bootstrap} cuando la corre Gradle). Se busca por {@code packages/contratos} y no por
   * {@code .git}, porque lo que hace falta es justo esa carpeta y no el repositorio.
   */
  private static Path raizDelRepositorio() {
    Path candidato = Path.of("").toAbsolutePath();
    while (candidato != null && !Files.isDirectory(candidato.resolve("packages/contratos"))) {
      candidato = candidato.getParent();
    }
    if (candidato == null) {
      throw new IllegalStateException(
          "No se encontró packages/contratos subiendo desde " + Path.of("").toAbsolutePath());
    }
    return candidato;
  }
}
