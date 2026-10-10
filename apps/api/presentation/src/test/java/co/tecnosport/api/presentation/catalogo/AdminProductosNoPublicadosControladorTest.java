package co.tecnosport.api.presentation.catalogo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.tecnosport.api.application.catalogo.EliminarProducto;
import co.tecnosport.api.application.catalogo.EliminarProductosNoPublicados;
import co.tecnosport.api.application.catalogo.ProductosNoPublicados;
import co.tecnosport.api.domain.catalogo.Categoria;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.catalogo.Marca;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.compartido.Slug;
import co.tecnosport.api.presentation.ManejadorDeErrores;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;

/** El caso de uso de verdad sobre dobles: aquí se prueban la ruta, el cursor y la respuesta. */
@WebMvcTest(AdminProductosNoPublicadosControlador.class)
@Import({AdminProductosNoPublicadosControladorTest.Configuracion.class, ManejadorDeErrores.class})
class AdminProductosNoPublicadosControladorTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private RepositorioProductosDobleDePrueba productos;
  @Autowired private NoPublicadosFijos noPublicados;

  private Producto morral;

  @BeforeEach
  void unProductoEnBorrador() {
    productos.limpiar();
    morral =
        Producto.crear(
            "Morral urbano",
            new Slug("morral-urbano"),
            "",
            Marca.crear("TecnoSport"),
            Categoria.crear("Morrales", new Slug("bolsos-dama-morrales"), LineaCatalogo.BOLSOS));
    productos.conProductos(morral);
    noPublicados.ids.clear();
    noPublicados.ids.add(morral.id());
    noPublicados.pedidos.clear();
  }

  @Test
  void cuentaLosNoPublicados() throws Exception {
    mockMvc
        .perform(get("/api/v1/admin/productos/no-publicados"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.cantidad").value(1));
  }

  @Test
  void unaTandaBorraYAlNoLlenarseNoDaCursor() throws Exception {
    mockMvc
        .perform(delete("/api/v1/admin/productos/no-publicados"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.eliminados").value(1))
        .andExpect(jsonPath("$.conservadosPorVentas").value(0))
        .andExpect(jsonPath("$.conservadosPorExistencias").value(0))
        .andExpect(jsonPath("$.siguiente").doesNotExist())
        .andExpect(jsonPath("$.hasta").value(morral.id().toString()));

    assertThat(productos.productosEliminados).containsExactly(morral.id());
    assertThat(noPublicados.pedidos).containsExactly((UUID) null);
  }

  @Test
  void elCursorLlegaAlCasoDeUso() throws Exception {
    UUID desde = UUID.randomUUID();

    mockMvc
        .perform(
            delete("/api/v1/admin/productos/no-publicados")
                .param("desde", desde.toString())
                .param("hasta", morral.id().toString()))
        .andExpect(status().isOk());

    assertThat(noPublicados.pedidos).containsExactly(desde);
  }

  /** Lo que el adaptador devolvería, y con qué cursor se le preguntó. */
  static final class NoPublicadosFijos implements ProductosNoPublicados {
    final List<UUID> ids = new ArrayList<>();
    final List<UUID> pedidos = new ArrayList<>();

    @Override
    public long contar() {
      return ids.size();
    }

    @Override
    public Optional<UUID> ultimo() {
      return ids.isEmpty() ? Optional.empty() : Optional.of(ids.getLast());
    }

    @Override
    public List<UUID> ids(UUID despuesDe, UUID hasta, int limite) {
      pedidos.add(despuesDe);
      return List.copyOf(ids);
    }
  }

  @TestConfiguration
  static class Configuracion {

    @Bean
    RepositorioProductosDobleDePrueba repositorioProductos() {
      return new RepositorioProductosDobleDePrueba();
    }

    @Bean
    NoPublicadosFijos noPublicados() {
      return new NoPublicadosFijos();
    }

    @Bean
    EliminarProductosNoPublicados eliminarProductosNoPublicados(
        NoPublicadosFijos noPublicados, RepositorioProductosDobleDePrueba productos) {
      return new EliminarProductosNoPublicados(
          noPublicados,
          productos,
          new RepositorioInventarioDobleDePrueba(),
          new EliminarProducto(
              productos,
              new RepositorioPedidosParaBorradoDobleDePrueba(),
              new AlmacenDeImagenesDobleDePrueba()));
    }

    @Bean
    PlatformTransactionManager transactionManager() {
      return new PlatformTransactionManagerDobleDePrueba();
    }
  }
}
