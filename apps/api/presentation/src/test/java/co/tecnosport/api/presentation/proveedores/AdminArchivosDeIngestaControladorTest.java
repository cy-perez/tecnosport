package co.tecnosport.api.presentation.proveedores;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.proveedores.ArchivoDeIngestaEnLista;
import co.tecnosport.api.application.proveedores.ArchivosDeIngestaPaginados;
import co.tecnosport.api.application.proveedores.BorrarArchivoDeIngesta;
import co.tecnosport.api.application.proveedores.RepositorioArchivosDeIngesta;
import co.tecnosport.api.domain.proveedores.ArchivoDeIngesta;
import co.tecnosport.api.presentation.ManejadorDeErrores;
import co.tecnosport.api.presentation.proveedores.AdminIngestaControladorTest.AlmacenDoble;
import co.tecnosport.api.presentation.proveedores.AdminIngestaControladorTest.TransaccionEspia;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/** El caso de uso de verdad sobre dobles: rutas, cuerpos, y el 409 de un archivo en uso. */
@WebMvcTest(AdminArchivosDeIngestaControlador.class)
@Import({AdminArchivosDeIngestaControladorTest.Configuracion.class, ManejadorDeErrores.class})
class AdminArchivosDeIngestaControladorTest {

  private static final Instant AHORA = Instant.parse("2026-10-10T15:00:00Z");
  private static final String KEY = "proveedores/p/exportaciones/abc.zip";

  @Autowired private MockMvc mockMvc;
  @Autowired private ArchivosDoble archivos;
  @Autowired private AlmacenDoble almacen;

  private ArchivoDeIngesta archivo;

  @BeforeEach
  void unZipSubido() {
    archivos.porId.clear();
    archivos.enUso.clear();
    almacen.objetos.clear();
    archivo =
        new ArchivoDeIngesta(
            UUID.randomUUID(),
            KEY,
            UUID.randomUUID(),
            "Chat con Bolsos.zip",
            2_048L,
            AHORA.minusSeconds(86_400),
            null);
    archivos.guardar(archivo);
    almacen.objetos.put(KEY, new byte[2_048]);
  }

  /** Sin la key del bucket en la respuesta: es la ruta de un objeto privado. */
  @Test
  void listarDevuelveElHistorialSinLaKey() throws Exception {
    mockMvc
        .perform(get("/api/v1/admin/ingestas/archivos"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalArchivos").value(1))
        .andExpect(jsonPath("$.items[0].id").value(archivo.id().toString()))
        .andExpect(jsonPath("$.items[0].nombreOriginal").value("Chat con Bolsos.zip"))
        .andExpect(jsonPath("$.items[0].tamanoBytes").value(2_048))
        .andExpect(jsonPath("$.items[0].lotes").value(1))
        .andExpect(jsonPath("$.items[0].enUso").value(false))
        .andExpect(jsonPath("$.items[0].borradoEn").doesNotExist())
        .andExpect(jsonPath("$.items[0].referencia").doesNotExist());
  }

  @Test
  void borrarEs204YSeLlevaSoloElObjeto() throws Exception {
    mockMvc
        .perform(delete("/api/v1/admin/ingestas/archivos/{id}", archivo.id()))
        .andExpect(status().isNoContent());

    assertThat(almacen.objetos).doesNotContainKey(KEY);
    assertThat(archivos.porId.get(archivo.id()).borradoEn()).contains(AHORA);
  }

  @Test
  void conUnLoteAbiertoEs409ConSuCodigo() throws Exception {
    archivos.enUso.add(KEY);

    mockMvc
        .perform(delete("/api/v1/admin/ingestas/archivos/{id}", archivo.id()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.codigo").value("ARCHIVO_DE_INGESTA_EN_USO"));

    assertThat(almacen.objetos).containsKey(KEY);
  }

  @Test
  void elQueNoExisteEs404() throws Exception {
    mockMvc
        .perform(delete("/api/v1/admin/ingestas/archivos/{id}", UUID.randomUUID()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.codigo").value("ARCHIVO_DE_INGESTA_NO_ENCONTRADO"));
  }

  static final class ArchivosDoble implements RepositorioArchivosDeIngesta {
    final Map<UUID, ArchivoDeIngesta> porId = new LinkedHashMap<>();
    final Set<String> enUso = new HashSet<>();

    @Override
    public void guardar(ArchivoDeIngesta archivo) {
      porId.put(archivo.id(), archivo);
    }

    @Override
    public Optional<ArchivoDeIngesta> buscarPorId(UUID id) {
      return Optional.ofNullable(porId.get(id));
    }

    @Override
    public ArchivosDeIngestaPaginados listar(UUID proveedorId, int pagina, int tamanoPagina) {
      List<ArchivoDeIngestaEnLista> items =
          porId.values().stream()
              .map(a -> new ArchivoDeIngestaEnLista(a, 1, enUso.contains(a.referencia())))
              .toList();
      return new ArchivosDeIngestaPaginados(items, 0, 1, items.size());
    }

    @Override
    public boolean enUso(String referencia) {
      return enUso.contains(referencia);
    }
  }

  @TestConfiguration
  static class Configuracion {

    @Bean
    ArchivosDoble archivos() {
      return new ArchivosDoble();
    }

    @Bean
    AlmacenDoble almacen() {
      return new AlmacenDoble();
    }

    @Bean
    BorrarArchivoDeIngesta borrarArchivoDeIngesta(ArchivosDoble archivos, AlmacenDoble almacen) {
      return new BorrarArchivoDeIngesta(archivos, almacen, (Reloj) () -> AHORA);
    }

    @Bean
    TransaccionEspia transactionManager() {
      return new TransaccionEspia();
    }
  }
}
