package co.tecnosport.api.presentation.proveedores;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.tecnosport.api.application.catalogo.UrlFirmada;
import co.tecnosport.api.application.proveedores.AlmacenDeArchivosDeProveedor;
import co.tecnosport.api.application.proveedores.CrearProveedor;
import co.tecnosport.api.application.proveedores.DependenciasDeProveedor;
import co.tecnosport.api.application.proveedores.EditarProveedor;
import co.tecnosport.api.application.proveedores.EliminarProveedor;
import co.tecnosport.api.application.proveedores.RepositorioProveedores;
import co.tecnosport.api.domain.proveedores.OrdenDePublicacion;
import co.tecnosport.api.domain.proveedores.Proveedor;
import co.tecnosport.api.presentation.ManejadorDeErrores;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
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
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

@WebMvcTest(AdminProveedorControlador.class)
@Import({AdminProveedorControladorTest.Configuracion.class, ManejadorDeErrores.class})
class AdminProveedorControladorTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private RepositorioDoble proveedores;

  @BeforeEach
  void limpio() {
    proveedores.porId.clear();
    proveedores.dependencias = new DependenciasDeProveedor(0, false, List.of());
  }

  @Test
  void crearResponde201YElProveedorNaceActivo() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/admin/proveedores")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"nombre\":\"Meraki\",\"linea\":\"ROPA\",\"telefonoWhatsApp\":\"+57 321\","
                        + "\"nombreEnExportacion\":\"Meraki Cúcuta\",\"factorDeMargen\":1.30,"
                        + "\"ordenDePublicacion\":\"TEXTO_PRIMERO\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.ordenDePublicacion").value("TEXTO_PRIMERO"))
        .andExpect(jsonPath("$.activo").value(true))
        .andExpect(jsonPath("$.publicacionAutomatica").value(false))
        .andExpect(jsonPath("$.factorDeMargen").value(1.30));
  }

  /** La regla del dominio llega como 422 y con su mensaje: es lo que el panel enseña. */
  @Test
  void unaLineaQueNoEntraPorWhatsAppEs422() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/admin/proveedores")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"nombre\":\"Cel\",\"linea\":\"TECNOLOGIA\",\"telefonoWhatsApp\":\"+57\","
                        + "\"nombreEnExportacion\":\"Cel\",\"ordenDePublicacion\":\"FOTOS_PRIMERO\"}"))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.codigo").value("EXCEPCION_DE_DOMINIO"));
  }

  /**
   * El orden no tiene un valor que el servidor pueda suponer: sin él, el cuerpo no se lee. Y un
   * valor que no existe tampoco pasa, sin enseñar el nombre de la clase del enum.
   */
  @Test
  void sinOrdenDePublicacionOConUnoQueNoExisteEs422() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/admin/proveedores")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"nombre\":\"Meraki\",\"linea\":\"ROPA\",\"telefonoWhatsApp\":\"+57 321\","
                        + "\"nombreEnExportacion\":\"Meraki\"}"))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.codigo").value("HTTP_MESSAGE_NOT_READABLE"));

    mockMvc
        .perform(
            post("/api/v1/admin/proveedores")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"nombre\":\"Meraki\",\"linea\":\"ROPA\",\"telefonoWhatsApp\":\"+57 321\","
                        + "\"nombreEnExportacion\":\"Meraki\",\"ordenDePublicacion\":\"AL_REVES\"}"))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.detail").value(not(containsString("OrdenDePublicacion"))));
    assertTrue(proveedores.porId.isEmpty());
  }

  @Test
  void editarConservaLoQueElCuerpoNoTrae() throws Exception {
    Proveedor proveedor =
        Proveedor.crear(
            "Bolsos",
            co.tecnosport.api.domain.catalogo.LineaCatalogo.BOLSOS,
            "+57 300",
            "Bolsos",
            null,
            OrdenDePublicacion.FOTOS_PRIMERO);
    proveedores.porId.put(proveedor.id(), proveedor);

    mockMvc
        .perform(
            put("/api/v1/admin/proveedores/{id}", proveedor.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"nombre\":\"Bolsos del Centro\",\"linea\":\"BOLSOS\",\"telefonoWhatsApp\":\"+57 300\","
                        + "\"nombreEnExportacion\":\"Bolsos Centro\",\"activo\":false,"
                        + "\"ordenDePublicacion\":\"TEXTO_PRIMERO\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ordenDePublicacion").value("TEXTO_PRIMERO"))
        .andExpect(jsonPath("$.nombre").value("Bolsos del Centro"))
        .andExpect(jsonPath("$.activo").value(false))
        .andExpect(jsonPath("$.publicacionAutomatica").value(false));

    mockMvc
        .perform(get("/api/v1/admin/proveedores"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].nombreEnExportacion").value("Bolsos Centro"));
  }

  @Test
  void editarUnoQueNoExisteEs404() throws Exception {
    mockMvc
        .perform(
            put("/api/v1/admin/proveedores/{id}", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"nombre\":\"X\",\"linea\":\"BOLSOS\",\"telefonoWhatsApp\":\"+57\","
                        + "\"nombreEnExportacion\":\"X\",\"ordenDePublicacion\":\"FOTOS_PRIMERO\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.codigo").value("PROVEEDOR_NO_ENCONTRADO"));
  }

  @Test
  void eliminarUnoSinProductosResponde204YDesaparece() throws Exception {
    Proveedor proveedor = unoGuardado();

    mockMvc
        .perform(delete("/api/v1/admin/proveedores/{id}", proveedor.id()))
        .andExpect(status().isNoContent());

    mockMvc.perform(get("/api/v1/admin/proveedores")).andExpect(jsonPath("$.length()").value(0));
  }

  /** El panel dice cuántos con {@code productos}, sin leer la frase del {@code detail}. */
  @Test
  void eliminarUnoConProductosEs409YDiceCuantos() throws Exception {
    Proveedor proveedor = unoGuardado();
    proveedores.dependencias = new DependenciasDeProveedor(3, false, List.of());

    mockMvc
        .perform(delete("/api/v1/admin/proveedores/{id}", proveedor.id()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.codigo").value("PROVEEDOR_CON_PRODUCTOS"))
        .andExpect(jsonPath("$.productos").value(3));
  }

  @Test
  void eliminarUnoConIngestaEnCursoEs409() throws Exception {
    Proveedor proveedor = unoGuardado();
    proveedores.dependencias = new DependenciasDeProveedor(0, true, List.of());

    mockMvc
        .perform(delete("/api/v1/admin/proveedores/{id}", proveedor.id()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.codigo").value("PROVEEDOR_CON_INGESTA_EN_CURSO"));
  }

  @Test
  void eliminarUnoQueNoExisteEs404() throws Exception {
    mockMvc
        .perform(delete("/api/v1/admin/proveedores/{id}", UUID.randomUUID()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.codigo").value("PROVEEDOR_NO_ENCONTRADO"));
  }

  private Proveedor unoGuardado() {
    Proveedor proveedor =
        Proveedor.crear(
            "Bolsos",
            co.tecnosport.api.domain.catalogo.LineaCatalogo.BOLSOS,
            "+57 300",
            "Bolsos",
            null,
            OrdenDePublicacion.FOTOS_PRIMERO);
    proveedores.porId.put(proveedor.id(), proveedor);
    return proveedor;
  }

  @TestConfiguration
  static class Configuracion {
    @Bean
    RepositorioDoble repositorioProveedores() {
      return new RepositorioDoble();
    }

    @Bean
    CrearProveedor crearProveedor(RepositorioDoble repositorio) {
      return new CrearProveedor(repositorio);
    }

    @Bean
    EditarProveedor editarProveedor(RepositorioDoble repositorio) {
      return new EditarProveedor(repositorio);
    }

    @Bean
    EliminarProveedor eliminarProveedor(RepositorioDoble repositorio) {
      return new EliminarProveedor(repositorio, new AlmacenSinUso());
    }

    @Bean
    PlatformTransactionManager transactionManager() {
      return new AbstractPlatformTransactionManager() {
        @Override
        protected Object doGetTransaction() {
          return new Object();
        }

        @Override
        protected void doBegin(
            Object transaction, org.springframework.transaction.TransactionDefinition d) {}

        @Override
        protected void doCommit(DefaultTransactionStatus status) {}

        @Override
        protected void doRollback(DefaultTransactionStatus status) {}
      };
    }
  }

  static final class RepositorioDoble implements RepositorioProveedores {
    final Map<UUID, Proveedor> porId = new TreeMap<>();
    DependenciasDeProveedor dependencias = new DependenciasDeProveedor(0, false, List.of());

    @Override
    public void guardar(Proveedor proveedor) {
      porId.put(proveedor.id(), proveedor);
    }

    @Override
    public void actualizar(Proveedor proveedor) {
      porId.put(proveedor.id(), proveedor);
    }

    @Override
    public Optional<Proveedor> buscarPorId(UUID id) {
      return Optional.ofNullable(porId.get(id));
    }

    @Override
    public List<Proveedor> listar() {
      return new ArrayList<>(porId.values());
    }

    @Override
    public DependenciasDeProveedor dependenciasDe(UUID id) {
      return dependencias;
    }

    @Override
    public void eliminarConSuHistorial(UUID id) {
      porId.remove(id);
    }
  }

  /** Las dependencias del doble no traen archivos: si alguien llama, la prueba está mal armada. */
  static final class AlmacenSinUso implements AlmacenDeArchivosDeProveedor {

    @Override
    public UrlFirmada generarUrlDeSubida(String objectKey, String contentType) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Optional<Long> tamanoBytes(String objectKey) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void guardar(String objectKey, String contentType, byte[] bytes) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Optional<byte[]> leer(String objectKey) {
      throw new UnsupportedOperationException();
    }

    @Override
    public UrlFirmada urlDeLectura(String objectKey) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void borrar(String objectKey) {
      throw new UnsupportedOperationException();
    }
  }
}
