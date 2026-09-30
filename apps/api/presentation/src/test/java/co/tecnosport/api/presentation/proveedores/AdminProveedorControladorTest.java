package co.tecnosport.api.presentation.proveedores;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.tecnosport.api.application.proveedores.CrearProveedor;
import co.tecnosport.api.application.proveedores.EditarProveedor;
import co.tecnosport.api.application.proveedores.RepositorioProveedores;
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
  }

  @Test
  void crearResponde201YElProveedorNaceActivo() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/admin/proveedores")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"nombre\":\"Meraki\",\"linea\":\"ROPA\",\"telefonoWhatsApp\":\"+57 321\","
                        + "\"nombreEnExportacion\":\"Meraki Cúcuta\",\"factorDeMargen\":1.30}"))
        .andExpect(status().isCreated())
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
                        + "\"nombreEnExportacion\":\"Cel\"}"))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.codigo").value("EXCEPCION_DE_DOMINIO"));
  }

  @Test
  void editarConservaLoQueElCuerpoNoTrae() throws Exception {
    Proveedor proveedor =
        Proveedor.crear(
            "Bolsos",
            co.tecnosport.api.domain.catalogo.LineaCatalogo.BOLSOS,
            "+57 300",
            "Bolsos",
            null);
    proveedores.porId.put(proveedor.id(), proveedor);

    mockMvc
        .perform(
            put("/api/v1/admin/proveedores/{id}", proveedor.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"nombre\":\"Bolsos del Centro\",\"linea\":\"BOLSOS\",\"telefonoWhatsApp\":\"+57 300\","
                        + "\"nombreEnExportacion\":\"Bolsos Centro\",\"activo\":false}"))
        .andExpect(status().isOk())
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
                        + "\"nombreEnExportacion\":\"X\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.codigo").value("PROVEEDOR_NO_ENCONTRADO"));
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
  }
}
