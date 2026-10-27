package co.tecnosport.api.presentation.proveedores;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.tecnosport.api.application.proveedores.ListarVariantesSinMargen;
import co.tecnosport.api.application.proveedores.RepositorioProductosDeProveedor.VarianteSinMargen;
import co.tecnosport.api.presentation.ManejadorDeErrores;
import co.tecnosport.api.presentation.proveedores.AdminTecnologiaControladorTest.SinProductosDeProveedor;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/** El aviso del panel: el total y cada variante con su producto. */
@WebMvcTest(AdminVariantesSinMargenControlador.class)
@Import({AdminVariantesSinMargenControladorTest.Configuracion.class, ManejadorDeErrores.class})
class AdminVariantesSinMargenControladorTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ConSinMargen productosDeProveedor;

  @BeforeEach
  void limpio() {
    productosDeProveedor.sinMargen.clear();
  }

  @Test
  void devuelveElTotalYCadaVarianteConSuProducto() throws Exception {
    UUID producto = UUID.randomUUID();
    UUID variante = UUID.randomUUID();
    productosDeProveedor.sinMargen.add(
        new VarianteSinMargen(producto, "Samsung Galaxy A17 5G", variante, "A17-256-NEGRO"));

    mockMvc
        .perform(get("/api/v1/admin/variantes/sin-margen"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(1))
        .andExpect(jsonPath("$.items[0].productoId").value(producto.toString()))
        .andExpect(jsonPath("$.items[0].nombreProducto").value("Samsung Galaxy A17 5G"))
        .andExpect(jsonPath("$.items[0].varianteId").value(variante.toString()))
        .andExpect(jsonPath("$.items[0].sku").value("A17-256-NEGRO"));
  }

  @Test
  void sinNadaQueAvisarElTotalEsCero() throws Exception {
    mockMvc
        .perform(get("/api/v1/admin/variantes/sin-margen"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(0))
        .andExpect(jsonPath("$.items").isEmpty());
  }

  static final class ConSinMargen extends SinProductosDeProveedor {
    final List<VarianteSinMargen> sinMargen = new ArrayList<>();

    @Override
    public List<VarianteSinMargen> variantesSinMargen() {
      return List.copyOf(sinMargen);
    }
  }

  @TestConfiguration
  static class Configuracion {
    @Bean
    ConSinMargen productosDeProveedor() {
      return new ConSinMargen();
    }

    @Bean
    ListarVariantesSinMargen listarVariantesSinMargen(ConSinMargen productosDeProveedor) {
      return new ListarVariantesSinMargen(productosDeProveedor);
    }
  }
}
