package co.tecnosport.api.presentation.catalogo;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.tecnosport.api.application.catalogo.AbrirSetRotacion;
import co.tecnosport.api.application.catalogo.AlmacenDeImagenes;
import co.tecnosport.api.application.catalogo.CompletarSetRotacion;
import co.tecnosport.api.application.catalogo.EliminarSetRotacion;
import co.tecnosport.api.application.catalogo.PublicarSetRotacion;
import co.tecnosport.api.application.catalogo.RepositorioProductos;
import co.tecnosport.api.application.catalogo.RepositorioSetsRotacion;
import co.tecnosport.api.application.catalogo.SolicitarSubidasDeRotacion;
import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.domain.catalogo.Categoria;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.catalogo.Marca;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.catalogo.SetRotacion;
import co.tecnosport.api.domain.compartido.HashContenido;
import co.tecnosport.api.domain.compartido.Slug;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;

@WebMvcTest(AdminSetRotacionControlador.class)
@Import(AdminSetRotacionControladorTest.Configuracion.class)
class AdminSetRotacionControladorTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private RepositorioProductosDobleDePrueba repositorioProductos;
  @Autowired private RepositorioSetsRotacionDobleDePrueba repositorioSets;
  @Autowired private AlmacenDeImagenesDobleDePrueba almacenDeImagenes;

  @BeforeEach
  void autenticarAdmin() {
    repositorioProductos.limpiar();
    repositorioSets.limpiar();
    almacenDeImagenes.limpiar();
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(UUID.randomUUID(), null));
  }

  @AfterEach
  void limpiarContexto() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void abrirDevuelve201ConElSetEnBorrador() throws Exception {
    Producto producto = productoDePrueba();
    repositorioProductos.conProductos(producto);

    mockMvc
        .perform(
            post("/api/v1/admin/sets-rotacion")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"productoId":"%s","fotogramas":8,"dispositivo":"iPhone 14",
                     "versionAsistente":"v1"}
                    """
                        .formatted(producto.id())))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.estado").value("BORRADOR"))
        .andExpect(jsonPath("$.fotogramasPrometidos").value(8))
        .andExpect(jsonPath("$.imagenes").isEmpty());
  }

  @Test
  void abrirConProductoInexistenteDevuelve404() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/admin/sets-rotacion")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"productoId":"%s","fotogramas":8,"dispositivo":null,"versionAsistente":null}
                    """
                        .formatted(UUID.randomUUID())))
        .andExpect(status().isNotFound());
  }

  @Test
  void abrirConUnNumeroDeFotogramasImposibleDevuelve422() throws Exception {
    Producto producto = productoDePrueba();
    repositorioProductos.conProductos(producto);

    mockMvc
        .perform(
            post("/api/v1/admin/sets-rotacion")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"productoId":"%s","fotogramas":2,"dispositivo":null,"versionAsistente":null}
                    """
                        .formatted(producto.id())))
        .andExpect(status().isUnprocessableContent());
  }

  @Test
  void subidasDevuelve201ConUnaUrlPorFotograma() throws Exception {
    SetRotacion set = abierto(4);

    mockMvc
        .perform(
            post("/api/v1/admin/sets-rotacion/" + set.id() + "/subidas")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"contentType":"image/webp"}
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.length()").value(4))
        .andExpect(jsonPath("$[0].orden").value(0))
        .andExpect(jsonPath("$[3].objectKey").value(clave(set, 3)));
  }

  @Test
  void subidasDeUnSetInexistenteDevuelve404() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/admin/sets-rotacion/" + UUID.randomUUID() + "/subidas")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"contentType":"image/webp"}
                    """))
        .andExpect(status().isNotFound());
  }

  @Test
  void completarDevuelveElSetCompletoConSusFotogramas() throws Exception {
    SetRotacion set = abierto(4);
    for (int orden = 0; orden < 4; orden++) {
      almacenDeImagenes.conObjeto(clave(set, orden), 42_000);
    }

    mockMvc
        .perform(
            post("/api/v1/admin/sets-rotacion/" + set.id() + "/completar")
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpoDeCompletar(set, 4)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.estado").value("COMPLETO"))
        .andExpect(jsonPath("$.imagenes.length()").value(4))
        .andExpect(jsonPath("$.imagenes[0].orden").value(0));
  }

  @Test
  void completarConUnObjetoQueNuncaLlegoDevuelve404() throws Exception {
    SetRotacion set = abierto(4);
    almacenDeImagenes.conObjeto(clave(set, 0), 42_000);

    mockMvc
        .perform(
            post("/api/v1/admin/sets-rotacion/" + set.id() + "/completar")
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpoDeCompletar(set, 4)))
        .andExpect(status().isNotFound());
  }

  @Test
  void completarConMenosFotogramasDeLosPrometidosDevuelve422() throws Exception {
    SetRotacion set = abierto(8);
    for (int orden = 0; orden < 4; orden++) {
      almacenDeImagenes.conObjeto(clave(set, orden), 42_000);
    }

    mockMvc
        .perform(
            post("/api/v1/admin/sets-rotacion/" + set.id() + "/completar")
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpoDeCompletar(set, 4)))
        .andExpect(status().isUnprocessableContent());
  }

  @Test
  void publicarDevuelveElSetPublicado() throws Exception {
    SetRotacion set = completo(4);

    mockMvc
        .perform(post("/api/v1/admin/sets-rotacion/" + set.id() + "/publicar"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.estado").value("PUBLICADO"));
  }

  @Test
  void publicarUnSetEnBorradorDevuelve422() throws Exception {
    SetRotacion set = abierto(4);

    mockMvc
        .perform(post("/api/v1/admin/sets-rotacion/" + set.id() + "/publicar"))
        .andExpect(status().isUnprocessableContent());
  }

  @Test
  void publicarUnSegundoSetDelMismoProductoDevuelve409() throws Exception {
    UUID producto = UUID.randomUUID();
    SetRotacion anterior = completo(4, producto);
    anterior.publicar();
    repositorioSets.con(anterior);
    SetRotacion nuevo = completo(4, producto);

    mockMvc
        .perform(post("/api/v1/admin/sets-rotacion/" + nuevo.id() + "/publicar"))
        .andExpect(status().isConflict());
  }

  @Test
  void eliminarDevuelve204() throws Exception {
    SetRotacion set = abierto(4);

    mockMvc
        .perform(delete("/api/v1/admin/sets-rotacion/" + set.id()))
        .andExpect(status().isNoContent());
  }

  @Test
  void eliminarUnSetInexistenteDevuelve404() throws Exception {
    mockMvc
        .perform(delete("/api/v1/admin/sets-rotacion/" + UUID.randomUUID()))
        .andExpect(status().isNotFound());
  }

  private SetRotacion abierto(int fotogramas) {
    return abierto(fotogramas, UUID.randomUUID());
  }

  private SetRotacion abierto(int fotogramas, UUID productoId) {
    SetRotacion set = SetRotacion.abrir(productoId, fotogramas, "admin:1", null, "iPhone 14", "v1");
    repositorioSets.con(set);
    return set;
  }

  private SetRotacion completo(int fotogramas) {
    return completo(fotogramas, UUID.randomUUID());
  }

  private SetRotacion completo(int fotogramas, UUID productoId) {
    SetRotacion set = abierto(fotogramas, productoId);
    for (int orden = 0; orden < fotogramas; orden++) {
      set.agregarFotograma(
          co.tecnosport.api.domain.catalogo.ImagenProducto.crear(
              co.tecnosport.api.domain.catalogo.TipoImagen.ROTACION,
              orden,
              "https://x/" + orden,
              "https://x/" + orden,
              1000,
              1000,
              900,
              new HashContenido("%064x".formatted(orden)),
              null,
              null));
    }
    set.completar();
    return set;
  }

  private static String clave(SetRotacion set, int orden) {
    return "productos/" + set.productoId() + "/rotacion/" + set.id() + "/" + orden + ".webp";
  }

  private static String cuerpoDeCompletar(SetRotacion set, int cuantos) {
    StringBuilder fotogramas = new StringBuilder();
    for (int orden = 0; orden < cuantos; orden++) {
      if (orden > 0) {
        fotogramas.append(',');
      }
      fotogramas
          .append("{\"orden\":")
          .append(orden)
          .append(",\"objectKey\":\"")
          .append(clave(set, orden))
          .append("\",\"ancho\":1000,\"alto\":1000,\"hash\":\"")
          .append("%064x".formatted(orden + 1))
          .append("\"}");
    }
    return "{\"fotogramas\":[" + fotogramas + "]}";
  }

  private static Producto productoDePrueba() {
    Marca marca = Marca.crear("TecnoSport");
    Categoria categoria = Categoria.crear("Bolsos", new Slug("bolsos"), LineaCatalogo.BOLSOS);
    return Producto.crear("Morral urbano", new Slug("morral-urbano"), "", marca, categoria);
  }

  @TestConfiguration
  static class Configuracion {

    @Bean
    RepositorioProductosDobleDePrueba repositorioProductos() {
      return new RepositorioProductosDobleDePrueba();
    }

    @Bean
    RepositorioSetsRotacionDobleDePrueba repositorioSets() {
      return new RepositorioSetsRotacionDobleDePrueba();
    }

    @Bean
    AlmacenDeImagenesDobleDePrueba almacenDeImagenes() {
      return new AlmacenDeImagenesDobleDePrueba();
    }

    @Bean
    Reloj reloj() {
      return Instant::now;
    }

    @Bean
    PlatformTransactionManager transactionManager() {
      return new PlatformTransactionManagerDobleDePrueba();
    }

    @Bean
    AbrirSetRotacion abrirSetRotacion(
        RepositorioProductos repositorioProductos,
        RepositorioSetsRotacion repositorioSets,
        Reloj reloj) {
      return new AbrirSetRotacion(repositorioProductos, repositorioSets, reloj);
    }

    @Bean
    SolicitarSubidasDeRotacion solicitarSubidasDeRotacion(
        RepositorioSetsRotacion repositorioSets, AlmacenDeImagenes almacenDeImagenes) {
      return new SolicitarSubidasDeRotacion(repositorioSets, almacenDeImagenes);
    }

    @Bean
    CompletarSetRotacion completarSetRotacion(
        RepositorioSetsRotacion repositorioSets, AlmacenDeImagenes almacenDeImagenes) {
      return new CompletarSetRotacion(repositorioSets, almacenDeImagenes);
    }

    @Bean
    PublicarSetRotacion publicarSetRotacion(RepositorioSetsRotacion repositorioSets) {
      return new PublicarSetRotacion(repositorioSets);
    }

    @Bean
    EliminarSetRotacion eliminarSetRotacion(
        RepositorioSetsRotacion repositorioSets, AlmacenDeImagenes almacenDeImagenes) {
      return new EliminarSetRotacion(repositorioSets, almacenDeImagenes);
    }

    @Bean
    MapeadorRespuestasSetRotacion mapeadorRespuestasSetRotacion() {
      return new MapeadorRespuestasSetRotacion();
    }
  }
}
