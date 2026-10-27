package co.tecnosport.api.presentation.catalogo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.tecnosport.api.application.catalogo.PreciosDeVariante;
import co.tecnosport.api.application.catalogo.PreciosDelCatalogo;
import co.tecnosport.api.application.catalogo.RecalcularPreciosPublicados;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.presentation.ManejadorDeErrores;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;

/** El caso de uso de verdad, con las tarifas de hoy, sobre un catálogo en memoria. */
@WebMvcTest(AdminPreciosControlador.class)
@Import({AdminPreciosControladorTest.Configuracion.class, ManejadorDeErrores.class})
class AdminPreciosControladorTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private PreciosFijos precios;

  private UUID bolso;

  @BeforeEach
  void unoDesalineadoYOtroAlDia() {
    precios.variantes.clear();
    precios.grabados.clear();
    bolso = UUID.randomUUID();
    precios.variantes.add(
        new PreciosDeVariante(
            bolso, "TS-BOLSO", "Bolso Lucía", Dinero.deCop(230_000), Dinero.deCop(230_000)));
    precios.variantes.add(
        new PreciosDeVariante(
            UUID.randomUUID(),
            "TS-CEL",
            "Moto G17",
            Dinero.deCop(1_000_000),
            Dinero.deCop(1_034_000)));
  }

  @Test
  void elGetEnsenaLoQueCambiariaSinGrabarNada() throws Exception {
    mockMvc
        .perform(get("/api/v1/admin/precios/recalculo"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.variantes.length()").value(1))
        .andExpect(jsonPath("$.variantes[0].varianteId").value(bolso.toString()))
        .andExpect(jsonPath("$.variantes[0].sku").value("TS-BOLSO"))
        .andExpect(jsonPath("$.variantes[0].nombreProducto").value("Bolso Lucía"))
        .andExpect(jsonPath("$.variantes[0].precioContado").value(230_000))
        .andExpect(jsonPath("$.variantes[0].publicadoActual").value(230_000))
        .andExpect(jsonPath("$.variantes[0].publicadoNuevo").value(239_000));

    assertThat(precios.grabados).isEmpty();
  }

  private static String fila(UUID id, long contado, long actual, long nuevo) {
    return "{\"varianteId\":\""
        + id
        + "\",\"precioContado\":"
        + contado
        + ",\"publicadoActual\":"
        + actual
        + ",\"publicadoNuevo\":"
        + nuevo
        + "}";
  }

  @Test
  void elPostAplicaLaListaRevisadaYDiceCuantas() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/admin/precios/recalculo")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"variantes\":[" + fila(bolso, 230_000, 230_000, 239_000) + "]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.recalculadas").value(1))
        .andExpect(jsonPath("$.omitidas").value(0));

    assertThat(precios.grabados).containsExactly(Map.entry(bolso, Dinero.deCop(239_000)));
  }

  /**
   * Entre la revisión y la confirmación alguien cambió el contado del bolso desde la ficha: la fila
   * que se vio ya no es la de la base, y se cuenta como omitida en vez de pisarla.
   */
  @Test
  void elPostOmiteLoQueCambioDesdeLaRevision() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/admin/precios/recalculo")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"variantes\":[" + fila(bolso, 220_000, 230_000, 229_000) + "]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.recalculadas").value(0))
        .andExpect(jsonPath("$.omitidas").value(1));

    assertThat(precios.grabados).isEmpty();
  }

  @Test
  void elPostSinLaListaSeRechaza() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/admin/precios/recalculo")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isUnprocessableContent());

    assertThat(precios.grabados).isEmpty();
  }

  /** Lo que el adaptador devolvería, y lo que se le pidió grabar. */
  static final class PreciosFijos implements PreciosDelCatalogo {
    final List<PreciosDeVariante> variantes = new ArrayList<>();
    final Map<UUID, Dinero> grabados = new LinkedHashMap<>();

    @Override
    public List<PreciosDeVariante> listar() {
      return List.copyOf(variantes);
    }

    /** Condicional como el adaptador: solo si la variante sigue como se dice. */
    @Override
    public boolean actualizarPublicado(
        UUID varianteId, Dinero contado, Dinero publicadoActual, Dinero publicadoNuevo) {
      boolean sigueIgual =
          variantes.stream()
              .anyMatch(
                  v ->
                      v.varianteId().equals(varianteId)
                          && v.contado().equals(contado)
                          && v.publicado().equals(publicadoActual));
      if (sigueIgual) {
        grabados.put(varianteId, publicadoNuevo);
      }
      return sigueIgual;
    }
  }

  @TestConfiguration
  static class Configuracion {

    @Bean
    PreciosFijos precios() {
      return new PreciosFijos();
    }

    @Bean
    RecalcularPreciosPublicados recalcularPreciosPublicados(PreciosFijos precios) {
      return new RecalcularPreciosPublicados(precios, TarifasDePrueba.WOMPI);
    }

    @Bean
    PlatformTransactionManager transactionManager() {
      return new PlatformTransactionManagerDobleDePrueba();
    }
  }
}
