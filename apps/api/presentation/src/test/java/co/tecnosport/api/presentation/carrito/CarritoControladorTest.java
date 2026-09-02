package co.tecnosport.api.presentation.carrito;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.tecnosport.api.application.carrito.ActualizarCantidadDeLinea;
import co.tecnosport.api.application.carrito.AgregarLineaAlCarrito;
import co.tecnosport.api.application.carrito.CrearCarrito;
import co.tecnosport.api.application.carrito.EliminarLineaDelCarrito;
import co.tecnosport.api.application.carrito.RepositorioCarrito;
import co.tecnosport.api.application.carrito.VerCarrito;
import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.domain.carrito.Carrito;
import co.tecnosport.api.presentation.carrito.dto.AgregarLineaRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CarritoControlador.class)
@Import(CarritoControladorTest.Configuracion.class)
class CarritoControladorTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private RepositorioCarritoDobleDePrueba repositorio;

  private final ObjectMapper json = new ObjectMapper();

  @Test
  void crearDevuelveUnCarritoVacio() throws Exception {
    mockMvc
        .perform(post("/api/v1/carritos"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").exists())
        .andExpect(jsonPath("$.usuarioId").isEmpty())
        .andExpect(jsonPath("$.lineas", hasSize(0)));
  }

  @Test
  void verUnIdInexistenteDevuelve404() throws Exception {
    mockMvc
        .perform(get("/api/v1/carritos/{id}", UUID.randomUUID()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.codigo").value("CARRITO_NO_ENCONTRADO"));
  }

  @Test
  void agregarLineaDevuelveElCarritoConLaLinea() throws Exception {
    Carrito carrito = Carrito.crear(null, Instant.now());
    repositorio.guardar(carrito);
    UUID varianteId = UUID.randomUUID();

    mockMvc
        .perform(
            post("/api/v1/carritos/{id}/lineas", carrito.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new AgregarLineaRequest(varianteId, 2))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lineas", hasSize(1)))
        .andExpect(jsonPath("$.lineas[0].varianteId").value(varianteId.toString()))
        .andExpect(jsonPath("$.lineas[0].cantidad").value(2));
  }

  @Test
  void agregarLaMismaVarianteDosVecesSumaCantidad() throws Exception {
    Carrito carrito = Carrito.crear(null, Instant.now());
    repositorio.guardar(carrito);
    UUID varianteId = UUID.randomUUID();
    String cuerpo = json.writeValueAsString(new AgregarLineaRequest(varianteId, 2));

    mockMvc.perform(
        post("/api/v1/carritos/{id}/lineas", carrito.id())
            .contentType(MediaType.APPLICATION_JSON)
            .content(cuerpo));

    mockMvc
        .perform(
            post("/api/v1/carritos/{id}/lineas", carrito.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lineas", hasSize(1)))
        .andExpect(jsonPath("$.lineas[0].cantidad").value(4));
  }

  @Test
  void agregarLineaSinVarianteIdDevuelve422() throws Exception {
    Carrito carrito = Carrito.crear(null, Instant.now());
    repositorio.guardar(carrito);

    mockMvc
        .perform(
            post("/api/v1/carritos/{id}/lineas", carrito.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"cantidad\": 2}"))
        .andExpect(status().isUnprocessableContent());
  }

  @Test
  void actualizarCantidadACeroDevuelve422() throws Exception {
    Carrito carrito = Carrito.crear(null, Instant.now());
    carrito.agregarLinea(UUID.randomUUID(), 1);
    repositorio.guardar(carrito);
    UUID lineaId = carrito.lineas().get(0).id();

    mockMvc
        .perform(
            patch("/api/v1/carritos/{id}/lineas/{lineaId}", carrito.id(), lineaId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"cantidad\": 0}"))
        .andExpect(status().isUnprocessableContent());
  }

  @Test
  void actualizarUnaLineaInexistenteDevuelve404() throws Exception {
    Carrito carrito = Carrito.crear(null, Instant.now());
    repositorio.guardar(carrito);

    mockMvc
        .perform(
            patch("/api/v1/carritos/{id}/lineas/{lineaId}", carrito.id(), UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"cantidad\": 3}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.codigo").value("LINEA_CARRITO_NO_ENCONTRADA"));
  }

  @Test
  void eliminarLineaLaQuitaDelCarritoDevuelto() throws Exception {
    Carrito carrito = Carrito.crear(null, Instant.now());
    carrito.agregarLinea(UUID.randomUUID(), 1);
    repositorio.guardar(carrito);
    UUID lineaId = carrito.lineas().get(0).id();

    mockMvc
        .perform(delete("/api/v1/carritos/{id}/lineas/{lineaId}", carrito.id(), lineaId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lineas", hasSize(0)));
  }

  @TestConfiguration
  static class Configuracion {

    @Bean
    RepositorioCarritoDobleDePrueba repositorioCarrito() {
      return new RepositorioCarritoDobleDePrueba();
    }

    @Bean
    Reloj reloj() {
      return Instant::now;
    }

    @Bean
    CrearCarrito crearCarrito(RepositorioCarrito repositorio, Reloj reloj) {
      return new CrearCarrito(repositorio, reloj);
    }

    @Bean
    VerCarrito verCarrito(RepositorioCarrito repositorio) {
      return new VerCarrito(repositorio);
    }

    @Bean
    AgregarLineaAlCarrito agregarLineaAlCarrito(RepositorioCarrito repositorio) {
      return new AgregarLineaAlCarrito(repositorio);
    }

    @Bean
    ActualizarCantidadDeLinea actualizarCantidadDeLinea(RepositorioCarrito repositorio) {
      return new ActualizarCantidadDeLinea(repositorio);
    }

    @Bean
    EliminarLineaDelCarrito eliminarLineaDelCarrito(RepositorioCarrito repositorio) {
      return new EliminarLineaDelCarrito(repositorio);
    }

    @Bean
    MapeadorRespuestasCarrito mapeadorRespuestasCarrito() {
      return new MapeadorRespuestasCarrito();
    }
  }
}
